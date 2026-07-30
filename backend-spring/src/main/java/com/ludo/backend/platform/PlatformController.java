package com.ludo.backend.platform;

import com.ludo.backend.platform.wallet.MatchEconomyService;
import com.ludo.backend.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Aakda platform integration: launch binding + live wallet proxy.
 */
@RestController
@RequestMapping("/api/platform")
public class PlatformController {

  private static final Logger log = LoggerFactory.getLogger(PlatformController.class);
  private static final Pattern MONGO_OBJECT_ID = Pattern.compile("^[a-fA-F0-9]{24}$");
  private static final Pattern SAFE_USER_ID = Pattern.compile("^[A-Za-z0-9_-]{3,64}$");

  @Value("${ludo.platform.shared-secret:}")
  private String sharedSecret;

  private final MatchEconomyService matchEconomy;
  private final UserService userService;
  private final GameHistoryService gameHistoryService;

  public PlatformController(
      MatchEconomyService matchEconomy,
      UserService userService,
      GameHistoryService gameHistoryService
  ) {
    this.matchEconomy = matchEconomy;
    this.userService = userService;
    this.gameHistoryService = gameHistoryService;
  }

  public record LaunchRequest(
      String userId,
      String gameId,
      String sessionId,
      String token,
      String returnUrl,
      String displayName
  ) {
  }

  /** Bind platform query params into the HTTP session (called by WebView frontend). */
  @PostMapping("/launch")
  public Map<String, Object> launch(@RequestBody LaunchRequest body, HttpServletRequest request) {
    String userId = requireValidUserId(body.userId());
    String gameId = blankTo(body.gameId(), "LUDO");
    String displayName = blankTo(body.displayName(), "Player");
    if (displayName.length() > 64) {
      displayName = displayName.substring(0, 64);
    }

    PlatformLaunchContext ctx = new PlatformLaunchContext(
        userId,
        gameId,
        blankTo(body.sessionId(), null),
        blankTo(body.token(), null),
        sanitizeReturnUrl(body.returnUrl()),
        displayName
    );
    HttpSession session = request.getSession(true);
    session.setAttribute(PlatformLaunchContext.SESSION_KEY, ctx);
    try {
      userService.upsertPlatformProfile(userId, displayName);
    } catch (Exception e) {
      log.warn("platform profile upsert failed userId={}: {}", userId, e.getMessage());
    }
    log.info("platform launch userId={} gameId={} sessionId={} name={}", userId, gameId, ctx.sessionId(), displayName);

    Double balance = null;
    String balanceError = null;
    if (matchEconomy.isLive()) {
      try {
        balance = matchEconomy.getBalance(userId);
      } catch (Exception e) {
        balanceError = e.getMessage();
        log.warn("launch balance fetch failed userId={}: {}", userId, e.getMessage());
      }
    }

    Map<String, Object> res = new LinkedHashMap<>();
    res.put("success", true);
    res.put("userId", ctx.userId());
    res.put("gameId", ctx.gameId());
    res.put("sessionId", ctx.sessionId());
    res.put("displayName", ctx.displayName());
    res.put("returnUrl", ctx.returnUrl());
    res.put("walletEnabled", matchEconomy.isLive());
    res.put("entryFee", matchEconomy.entryFee());
    res.put("betOptions", matchEconomy.betOptions());
    res.put("balance", balance);
    res.put("balanceError", balanceError);
    return res;
  }

  @GetMapping("/context")
  public Map<String, Object> context(HttpServletRequest request) {
    PlatformLaunchContext ctx = current(request);
    if (ctx == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No platform session");
    }
    Map<String, Object> res = new LinkedHashMap<>();
    res.put("success", true);
    res.put("userId", ctx.userId());
    res.put("gameId", ctx.gameId());
    res.put("sessionId", ctx.sessionId());
    res.put("displayName", ctx.displayName());
    res.put("returnUrl", ctx.returnUrl());
    res.put("walletEnabled", matchEconomy.isLive());
    res.put("entryFee", matchEconomy.entryFee());
    return res;
  }

  @GetMapping("/economy")
  public Map<String, Object> economy() {
    Map<String, Object> res = new LinkedHashMap<>();
    res.put("success", true);
    res.put("walletEnabled", matchEconomy.isLive());
    res.put("entryFee", matchEconomy.entryFee());
    res.put("betOptions", matchEconomy.betOptions());
    res.put("gameId", matchEconomy.gameId());
    return res;
  }

  /** Live Aakda wallet balance (never trust client). */
  @GetMapping("/balance")
  public Map<String, Object> balance(
      @RequestParam String userId,
      @RequestHeader(value = "X-Platform-Key", required = false) String platformKey
  ) {
    assertPlatformKey(platformKey);
    requireValidUserId(userId);
    if (!matchEconomy.isLive()) {
      Map<String, Object> res = new LinkedHashMap<>();
      res.put("success", true);
      res.put("userId", userId);
      res.put("balance", 0);
      res.put("currency", "INR");
      res.put("walletEnabled", false);
      return res;
    }
    double bal = matchEconomy.getBalance(userId);
    Map<String, Object> res = new LinkedHashMap<>();
    res.put("success", true);
    res.put("status", "SUCCESS");
    res.put("userId", userId);
    res.put("balance", bal);
    res.put("currency", "INR");
    res.put("walletEnabled", true);
    res.put("mock", false);
    return res;
  }

  /** Completed match history for one player (game id, date, bet, win, opponent, result). */
  @GetMapping("/history")
  public Map<String, Object> history(
      @RequestParam String userId,
      @RequestParam(required = false, defaultValue = "50") int limit
  ) {
    String id = requireValidUserId(userId);
    List<Map<String, Object>> games = gameHistoryService.historyForUser(id, limit);
    Map<String, Object> res = new LinkedHashMap<>();
    res.put("success", true);
    res.put("userId", id);
    res.put("games", games);
    res.put("count", games.size());
    return res;
  }

  private void assertPlatformKey(String platformKey) {
    if (sharedSecret == null || sharedSecret.isBlank()) {
      return;
    }
    if (platformKey == null || !sharedSecret.equals(platformKey)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid X-Platform-Key");
    }
  }

  static String requireValidUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
    }
    String id = userId.trim();
    if (MONGO_OBJECT_ID.matcher(id).matches() || SAFE_USER_ID.matcher(id).matches()) {
      return id;
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid userId format");
  }

  private static PlatformLaunchContext current(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      return null;
    }
    Object attr = session.getAttribute(PlatformLaunchContext.SESSION_KEY);
    return attr instanceof PlatformLaunchContext ctx ? ctx : null;
  }

  private static String blankTo(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }

  private static String sanitizeReturnUrl(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String u = raw.trim();
    if (!(u.startsWith("https://") || u.startsWith("http://"))) {
      return null;
    }
    return u.length() > 512 ? u.substring(0, 512) : u;
  }

  @org.springframework.web.bind.annotation.ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> handle(ResponseStatusException ex) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("success", false);
    body.put("error", ex.getReason() == null ? "error" : ex.getReason());
    return ResponseEntity.status(ex.getStatusCode()).body(body);
  }
}
