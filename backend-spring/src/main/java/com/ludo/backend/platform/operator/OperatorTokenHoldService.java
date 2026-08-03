package com.ludo.backend.platform.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ludo.backend.platform.PlatformLaunchContext;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;

/**
 * Distributed operator token hold for cashout (Phase 6B).
 *
 * <ul>
 *   <li><b>Redis</b> (preferred when {@code REDIS_URL} / Redis auto-config is present):
 *       key {@code ludo:operator:hold:{matchId}:{userId}} with TTL.</li>
 *   <li><b>Spring Session (Mongo)</b> fallback when Redis is absent: token stays only in
 *       {@link PlatformLaunchContext} inside the launch HTTP session; ledger stores
 *       {@code httpSessionId} (not the token) so any node can resolve it.</li>
 * </ul>
 *
 * <p>Never writes plaintext tokens to {@code match_economy}.
 */
@Service
@ConditionalOnProperty(prefix = "ludo.wallet", name = "mode", havingValue = "OPERATOR")
public class OperatorTokenHoldService {

  private static final Logger log = LoggerFactory.getLogger(OperatorTokenHoldService.class);
  private static final String KEY_PREFIX = "ludo:operator:hold:";

  private final Optional<StringRedisTemplate> redis;
  private final SessionRepository<? extends Session> sessions;
  private final Duration ttl;
  private final boolean redisActive;
  private final ObjectMapper mapper = new ObjectMapper();

  public OperatorTokenHoldService(
      ObjectProvider<StringRedisTemplate> redisProvider,
      SessionRepository<? extends Session> sessions,
      RabbitCashoutProperties cashoutProperties
  ) {
    this.redis = Optional.ofNullable(redisProvider.getIfAvailable());
    this.sessions = sessions;
    int ttlSeconds = cashoutProperties.holdTtlSeconds() > 0
        ? cashoutProperties.holdTtlSeconds()
        : 86_400;
    this.ttl = Duration.ofSeconds(ttlSeconds);
    this.redisActive = this.redis.isPresent();
    if (redisActive) {
      log.info(
          "operator token hold backend=REDIS ttlSeconds={} keyPrefix={}",
          ttl.getSeconds(),
          KEY_PREFIX
      );
    } else {
      log.warn(
          "operator token hold backend=SPRING_SESSION (Mongo HTTP session) — REDIS_URL is not "
              + "configured so Redis auto-config is off. Cashout retries on any node require the "
              + "original launch session to still exist (session TTL must cover the match + settle "
              + "window). For multi-instance production, set REDIS_URL so holds survive restarts "
              + "independently of the browser session."
      );
    }
  }

  public String backend() {
    return redisActive ? "REDIS" : "SPRING_SESSION";
  }

  public void put(String matchId, String userId, OperatorTokenHold hold) {
    if (matchId == null || userId == null || hold == null) {
      return;
    }
    if (redisActive) {
      try {
        String json = mapper.writeValueAsString(hold);
        redis.get().opsForValue().set(key(matchId, userId), json, ttl);
        log.info(
            "operator hold PUT backend=REDIS matchId={} userId={} ttlSeconds={}",
            matchId,
            userId,
            ttl.getSeconds()
        );
        return;
      } catch (Exception e) {
        log.error(
            "operator hold Redis PUT failed matchId={} userId={} — falling back to session pointer only: {}",
            matchId,
            userId,
            e.getMessage()
        );
      }
    }
    // Session fallback: token already lives in PlatformLaunchContext; no extra write.
    log.info(
        "operator hold PUT backend=SPRING_SESSION matchId={} userId={} sessionId={} "
            + "(token remains in HTTP session only; ensure session TTL >= match duration)",
        matchId,
        userId,
        hold.httpSessionId()
    );
  }

  public Optional<OperatorTokenHold> get(String matchId, String userId, String httpSessionId) {
    if (redisActive) {
      try {
        String json = redis.get().opsForValue().get(key(matchId, userId));
        if (json != null && !json.isBlank()) {
          OperatorTokenHold hold = mapper.readValue(json, OperatorTokenHold.class);
          if (hold != null && hold.token() != null && !hold.token().isBlank()) {
            return Optional.of(hold);
          }
        }
      } catch (Exception e) {
        log.error(
            "operator hold Redis GET failed matchId={} userId={}: {}",
            matchId,
            userId,
            e.getMessage()
        );
      }
    }
    return readFromSession(httpSessionId, userId);
  }

  public void remove(String matchId, String userId) {
    if (matchId == null || userId == null) {
      return;
    }
    if (redisActive) {
      try {
        Boolean deleted = redis.get().delete(key(matchId, userId));
        log.info(
            "operator hold REMOVE backend=REDIS matchId={} userId={} deleted={}",
            matchId,
            userId,
            deleted
        );
      } catch (Exception e) {
        log.error(
            "operator hold Redis REMOVE failed matchId={} userId={}: {}",
            matchId,
            userId,
            e.getMessage()
        );
      }
    }
    // Session tokens expire with the HTTP session; no Mongo token field to clear.
  }

  private Optional<OperatorTokenHold> readFromSession(String httpSessionId, String userId) {
    if (httpSessionId == null || httpSessionId.isBlank()) {
      log.warn("operator hold session GET skipped — no httpSessionId userId={}", userId);
      return Optional.empty();
    }
    try {
      Session session = sessions.findById(httpSessionId);
      if (session == null) {
        log.warn(
            "operator hold session GET miss sessionId={} userId={} (expired or unknown)",
            httpSessionId,
            userId
        );
        return Optional.empty();
      }
      Object attr = session.getAttribute(PlatformLaunchContext.SESSION_KEY);
      if (!(attr instanceof PlatformLaunchContext ctx)) {
        log.warn("operator hold session GET no PlatformLaunchContext sessionId={}", httpSessionId);
        return Optional.empty();
      }
      if (ctx.token() == null || ctx.token().isBlank()) {
        return Optional.empty();
      }
      if (userId != null && ctx.userId() != null && !userId.equals(ctx.userId())) {
        log.warn(
            "operator hold session user mismatch sessionId={} expected={} actual={}",
            httpSessionId,
            userId,
            ctx.userId()
        );
        return Optional.empty();
      }
      return Optional.of(new OperatorTokenHold(
          ctx.token(),
          ctx.operatorId(),
          "0.0.0.0",
          httpSessionId
      ));
    } catch (Exception e) {
      log.error(
          "operator hold session GET failed sessionId={} userId={}: {}",
          httpSessionId,
          userId,
          e.getMessage()
      );
      return Optional.empty();
    }
  }

  private static String key(String matchId, String userId) {
    return KEY_PREFIX + matchId + ":" + userId;
  }
}
