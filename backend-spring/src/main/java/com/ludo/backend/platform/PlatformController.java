package com.ludo.backend.platform;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
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
 * Aakda platform integration: launch binding + wallet stubs.
 *
 * <p>TODO: Wire debit/credit/balance to Aakda Node wallet APIs when ready.
 */
@RestController
@RequestMapping("/api/platform")
public class PlatformController {

  private static final Logger log = LoggerFactory.getLogger(PlatformController.class);
  private static final Pattern MONGO_OBJECT_ID = Pattern.compile("^[a-fA-F0-9]{24}$");
  private static final Pattern SAFE_USER_ID = Pattern.compile("^[A-Za-z0-9_-]{3,64}$");

  @Value("${ludo.platform.shared-secret:}")
  private String sharedSecret;

  public record LaunchRequest(
      String userId,
      String gameId,
      String sessionId,
      String token,
      String returnUrl,
      String displayName
  ) {
  }

  public record MoneyRequest(String userId, String sessionId, Double amount, String reason) {
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
    log.info("platform launch userId={} gameId={} sessionId={}", userId, gameId, ctx.sessionId());

    Map<String, Object> res = new LinkedHashMap<>();
    res.put("success", true);
    res.put("userId", ctx.userId());
    res.put("gameId", ctx.gameId());
    res.put("sessionId", ctx.sessionId());
    res.put("displayName", ctx.displayName());
    res.put("returnUrl", ctx.returnUrl());
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
    return res;
  }

  /**
   * Stub wallet balance.
   * TODO: Wire to Aakda Node wallet APIs
   */
  @GetMapping("/balance")
  public Map<String, Object> balance(
      @RequestParam String userId,
      @RequestHeader(value = "X-Platform-Key", required = false) String platformKey
  ) {
    assertPlatformKey(platformKey);
    requireValidUserId(userId);
    log.info("platform balance STUB userId={} (mock — not real money)", userId);
    Map<String, Object> res = new LinkedHashMap<>();
    res.put("success", true);
    res.put("userId", userId);
    res.put("balance", 1000.0);
    res.put("currency", "INR");
    res.put("mock", true);
    return res;
  }

  /**
   * Stub debit.
   * TODO: Wire to Aakda Node wallet APIs
   */
  @PostMapping("/debit")
  public Map<String, Object> debit(
      @RequestBody MoneyRequest body,
      @RequestHeader(value = "X-Platform-Key", required = false) String platformKey
  ) {
    assertPlatformKey(platformKey);
    requireValidUserId(body.userId());
    log.info(
        "platform debit STUB userId={} sessionId={} amount={} reason={} (mock — not real money)",
        body.userId(), body.sessionId(), body.amount(), body.reason()
    );
    return moneyStub("debit", body);
  }

  /**
   * Stub credit.
   * TODO: Wire to Aakda Node wallet APIs
   */
  @PostMapping("/credit")
  public Map<String, Object> credit(
      @RequestBody MoneyRequest body,
      @RequestHeader(value = "X-Platform-Key", required = false) String platformKey
  ) {
    assertPlatformKey(platformKey);
    requireValidUserId(body.userId());
    log.info(
        "platform credit STUB userId={} sessionId={} amount={} reason={} (mock — not real money)",
        body.userId(), body.sessionId(), body.amount(), body.reason()
    );
    return moneyStub("credit", body);
  }

  private Map<String, Object> moneyStub(String op, MoneyRequest body) {
    Map<String, Object> res = new LinkedHashMap<>();
    res.put("success", true);
    res.put("operation", op);
    res.put("userId", body.userId());
    res.put("sessionId", body.sessionId());
    res.put("amount", body.amount() == null ? 0 : body.amount());
    res.put("reason", body.reason());
    res.put("balance", 1000.0);
    res.put("mock", true);
    return res;
  }

  private void assertPlatformKey(String platformKey) {
    if (sharedSecret == null || sharedSecret.isBlank()) {
      // Dev: secret not set → allow
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
