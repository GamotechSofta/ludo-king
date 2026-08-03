package com.ludo.backend.platform.wallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Legacy external wallet HTTP client — the configured wallet API is the money source of truth.
 *
 * <p>Base URL must be the wallet API host (not a player frontend SPA). Frontend hosts often
 * answer wallet paths with an empty 200, so responses are validated strictly and a wrong host
 * is reported as a config error when {@code ludo.wallet.frontend-hosts} matches.
 */
@Service
public class LegacyWalletClient {

  private static final Logger log = LoggerFactory.getLogger(LegacyWalletClient.class);

  /** Body prefix length kept in logs when a response cannot be parsed. */
  private static final int LOG_BODY_CHARS = 200;

  private final WalletProperties props;
  private final RestTemplate restTemplate;
  private final ObjectMapper mapper = new ObjectMapper();

  public LegacyWalletClient(WalletProperties props, RestTemplateBuilder builder) {
    this.props = props;
    this.restTemplate = builder
        .setConnectTimeout(java.time.Duration.ofSeconds(5))
        .setReadTimeout(java.time.Duration.ofSeconds(15))
        .build();
    logResolvedConfig();
  }

  private void logResolvedConfig() {
    String base = props.base();
    if (!props.enabled()) {
      log.info("wallet DISABLED (LUDO_WALLET_ENABLED=false) — matches run on the local ledger");
      return;
    }
    if (base.isBlank()) {
      log.warn("wallet base URL blank — set LEGACY_WALLET_BASE_URL to the wallet API host to charge entry fees");
      return;
    }
    if (props.wrongHost()) {
      log.error(
          "wallet base URL {} matches a configured frontend host, not the wallet API — every wallet call will "
              + "return an empty 200. Set LEGACY_WALLET_BASE_URL to the API host",
          base
      );
      return;
    }
    log.info("wallet LIVE baseUrl={} gameId={} entryFee={}", base, props.gameId(), props.entryFee());
  }

  public boolean isLive() {
    return props.isLive();
  }

  public WalletResult getBalance(String userId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("userId", userId);
    return post("/api/wallet/balance", body, "balance");
  }

  public WalletResult debit(String userId, double amount, String transactionId, String gameId, String roundId) {
    return post("/api/wallet/debit", moneyBody(userId, amount, transactionId, gameId, roundId), "debit");
  }

  public WalletResult credit(String userId, double amount, String transactionId, String gameId, String roundId) {
    return post("/api/wallet/credit", moneyBody(userId, amount, transactionId, gameId, roundId), "credit");
  }

  /**
   * Reverses a debit through the wallet's dedicated rollback endpoint, which is
   * idempotent per original transactionId. Never falls back to a plain credit:
   * the wallet treats every credit as fresh money, so retrying that way after an
   * ambiguous failure (timeout, unreadable body) would refund twice.
   */
  public WalletResult rollback(String userId, String originalTransactionId, double amount, String gameId, String roundId) {
    Map<String, Object> body = moneyBody(userId, amount, originalTransactionId, gameId, roundId);
    return post("/api/wallet/rollback", body, "rollback");
  }

  private Map<String, Object> moneyBody(
      String userId, double amount, String transactionId, String gameId, String roundId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("userId", userId);
    body.put("amount", WalletProperties.money(amount));
    body.put("transactionId", transactionId);
    body.put("gameId", gameId == null ? props.gameId() : gameId);
    body.put("roundId", roundId);
    return body;
  }

  private WalletResult post(String path, Map<String, Object> body, String op) {
    if (!props.enabled() || props.base().isBlank()) {
      log.warn("wallet {} skipped — wallet disabled or LEGACY_WALLET_BASE_URL blank body={}", op, body);
      return WalletResult.disabled();
    }
    if (props.wrongHost()) {
      log.error(
          "wallet {} refused — LEGACY_WALLET_BASE_URL={} is a configured frontend host. Use the wallet API host",
          op,
          props.base()
      );
      return new WalletResult(
          false, WalletResult.CONFIG_ERROR, 0, txnOf(body), false, "Wallet base URL points at the frontend host");
    }

    // Wallet APIs are idempotent per transactionId, so replaying the identical body is
    // safe — but only worth doing when the failure was transport-level.
    WalletResult result = attempt(path, body, op);
    if (result.retryable()) {
      log.warn("wallet {} retrying once after {} txn={}", op, result.status(), txnOf(body));
      result = attempt(path, body, op);
    }
    return result;
  }

  private WalletResult attempt(String path, Map<String, Object> body, String op) {
    String url = props.base() + path;
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    try {
      log.info("wallet {} REQUEST url={} body={}", op, url, body);
      ResponseEntity<String> res =
          restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
      return parse(url, op, res.getStatusCode().value(), contentType(res.getHeaders()), res.getBody(), body);
    } catch (HttpStatusCodeException e) {
      int status = e.getStatusCode().value();
      String payload = e.getResponseBodyAsString();
      if (status >= 500) {
        log.error("wallet {} SERVER ERROR url={} status={} body={}", op, url, status, trim(payload));
        return new WalletResult(false, WalletResult.SERVER_ERROR, 0, txnOf(body), false, "Wallet server error");
      }
      // A 4xx is the wallet's verdict (invalid userId, user not found, low balance);
      // only treat it as a config problem when it is not the documented JSON.
      return parse(url, op, status, contentType(e.getResponseHeaders()), payload, body);
    } catch (ResourceAccessException e) {
      log.error("wallet {} UNREACHABLE url={} err={}", op, url, e.getMessage());
      return new WalletResult(
          false, WalletResult.CONNECTION_ERROR, 0, txnOf(body), false, "Wallet unreachable");
    } catch (RestClientException e) {
      log.error("wallet {} ERROR url={} err={}", op, url, e.getMessage());
      return new WalletResult(false, WalletResult.HTTP_ERROR, 0, txnOf(body), false, "Wallet call failed");
    }
  }

  /**
   * Accepts only the documented wallet JSON envelope. An empty, HTML or fieldless body
   * means we are talking to the wrong host or path, which must not be reported
   * as a busy wallet.
   */
  private WalletResult parse(
      String url, String op, int httpStatus, String contentType, String payload, Map<String, Object> body) {
    String raw = payload == null ? "" : payload.trim();
    if (raw.isEmpty()) {
      return configError(url, op, httpStatus, contentType, "empty body", raw, body);
    }
    if (contentType != null && !contentType.contains("json")) {
      return configError(url, op, httpStatus, contentType, "non-JSON content type", raw, body);
    }
    JsonNode node;
    try {
      node = mapper.readTree(raw);
    } catch (Exception e) {
      return configError(url, op, httpStatus, contentType, "unparseable body", raw, body);
    }
    if (!node.isObject() || (!node.has("success") && !node.has("status"))) {
      return configError(url, op, httpStatus, contentType, "missing success/status", raw, body);
    }

    boolean ok = node.path("success").asBoolean(false)
        || "SUCCESS".equalsIgnoreCase(node.path("status").asText(""));
    double balance = node.path("balance").asDouble(0);
    String txn = node.hasNonNull("transactionId") ? node.get("transactionId").asText() : txnOf(body);
    String status = node.hasNonNull("status") ? node.get("status").asText() : (ok ? "SUCCESS" : "FAILED");
    String message = node.path("message").asText("");
    if (ok) {
      log.info("wallet {} OK url={} balance={} txn={}", op, url, balance, txn);
    } else {
      log.warn(
          "wallet {} DECLINED url={} httpStatus={} status={} message={}",
          op, url, httpStatus, status, message
      );
    }
    return new WalletResult(ok, status, balance, txn, false, message);
  }

  private WalletResult configError(
      String url,
      String op,
      int httpStatus,
      String contentType,
      String reason,
      String raw,
      Map<String, Object> body) {
    log.error(
        "wallet {} CONFIG ERROR ({}) url={} httpStatus={} contentType={} bodyPrefix={} "
            + "— expected wallet API JSON. Check LEGACY_WALLET_BASE_URL points at the API host",
        op, reason, url, httpStatus, contentType, trim(raw)
    );
    return new WalletResult(
        false, WalletResult.CONFIG_ERROR, 0, txnOf(body), false, "Wallet responded with " + reason);
  }

  private static String contentType(HttpHeaders headers) {
    if (headers == null || headers.getContentType() == null) {
      return null;
    }
    return headers.getContentType().toString().toLowerCase();
  }

  private static String trim(String value) {
    if (value == null) {
      return "";
    }
    return value.length() <= LOG_BODY_CHARS ? value : value.substring(0, LOG_BODY_CHARS) + "…";
  }

  private static String txnOf(Map<String, Object> body) {
    Object txn = body.get("transactionId");
    return txn == null ? null : String.valueOf(txn);
  }

  public record WalletResult(
      boolean success,
      String status,
      double balance,
      String transactionId,
      boolean mock,
      /** Wallet provider explanation, e.g. "Invalid userId" — empty when transport failed. */
      String message
  ) {
    static final String CONFIG_ERROR = "WALLET_CONFIG_ERROR";
    static final String SERVER_ERROR = "WALLET_SERVER_ERROR";
    static final String CONNECTION_ERROR = "WALLET_UNREACHABLE";
    static final String HTTP_ERROR = "HTTP_ERROR";

    static WalletResult disabled() {
      return new WalletResult(false, "WALLET_DISABLED", 0, null, true, "");
    }

    /** Wrong host, wrong path or a non-JSON answer — retrying cannot help. */
    public boolean configError() {
      return CONFIG_ERROR.equals(status);
    }

    /** Transport-level failure; safe to replay because the wallet keys on transactionId. */
    public boolean retryable() {
      return SERVER_ERROR.equals(status) || CONNECTION_ERROR.equals(status);
    }
  }
}
