package com.ludo.backend.platform.wallet;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Aakda wallet HTTP client — Aakda is the money source of truth.
 */
@Service
public class AakdaWalletClient {

  private static final Logger log = LoggerFactory.getLogger(AakdaWalletClient.class);

  private final WalletProperties props;
  private final RestTemplate restTemplate;

  public AakdaWalletClient(WalletProperties props, RestTemplateBuilder builder) {
    this.props = props;
    this.restTemplate = builder
        .setConnectTimeout(java.time.Duration.ofSeconds(5))
        .setReadTimeout(java.time.Duration.ofSeconds(15))
        .build();
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
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("userId", userId);
    body.put("amount", WalletProperties.money(amount));
    body.put("transactionId", transactionId);
    body.put("gameId", gameId == null ? props.gameId() : gameId);
    body.put("roundId", roundId);
    return post("/api/wallet/debit", body, "debit");
  }

  public WalletResult credit(String userId, double amount, String transactionId, String gameId, String roundId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("userId", userId);
    body.put("amount", WalletProperties.money(amount));
    body.put("transactionId", transactionId);
    body.put("gameId", gameId == null ? props.gameId() : gameId);
    body.put("roundId", roundId);
    return post("/api/wallet/credit", body, "credit");
  }

  /**
   * Prefer Aakda rollback if available; otherwise credit with ROLLBACK_ prefix.
   */
  public WalletResult rollback(String userId, String originalTransactionId, double amount, String gameId, String roundId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("userId", userId);
    body.put("transactionId", originalTransactionId);
    body.put("amount", WalletProperties.money(amount));
    body.put("gameId", gameId == null ? props.gameId() : gameId);
    body.put("roundId", roundId);
    WalletResult attempt = post("/api/wallet/rollback", body, "rollback");
    if (attempt.success()) {
      return attempt;
    }
    String refundTxn = "ROLLBACK_" + originalTransactionId;
    log.warn("rollback endpoint failed — falling back to credit txn={}", refundTxn);
    return credit(userId, amount, refundTxn, gameId, roundId);
  }

  @SuppressWarnings("unchecked")
  private WalletResult post(String path, Map<String, Object> body, String op) {
    if (!props.enabled() || props.base().isBlank()) {
      log.warn("wallet {} skipped — wallet disabled or AAKDA_WALLET_BASE_URL blank body={}", op, body);
      return WalletResult.disabled();
    }
    String url = props.base() + path;
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    try {
      log.info("wallet {} REQUEST url={} body={}", op, url, body);
      ResponseEntity<Map> res = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
      Map<String, Object> resp = res.getBody() == null ? Map.of() : res.getBody();
      log.info("wallet {} RESPONSE status={} body={}", op, res.getStatusCode(), resp);
      boolean ok = res.getStatusCode().is2xxSuccessful()
          && (Boolean.TRUE.equals(resp.get("success"))
              || "SUCCESS".equalsIgnoreCase(String.valueOf(resp.get("status"))));
      double bal = toDouble(resp.get("balance"));
      String txn = resp.get("transactionId") != null
          ? String.valueOf(resp.get("transactionId"))
          : String.valueOf(body.get("transactionId"));
      String status = resp.get("status") != null ? String.valueOf(resp.get("status")) : (ok ? "SUCCESS" : "FAILED");
      return new WalletResult(ok, status, bal, txn, false);
    } catch (RestClientException e) {
      log.error("wallet {} ERROR url={} err={}", op, url, e.getMessage());
      return new WalletResult(false, "HTTP_ERROR", 0, String.valueOf(body.get("transactionId")), false);
    }
  }

  private static double toDouble(Object v) {
    if (v instanceof Number n) {
      return n.doubleValue();
    }
    if (v == null) {
      return 0;
    }
    try {
      return Double.parseDouble(String.valueOf(v));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  public record WalletResult(
      boolean success,
      String status,
      double balance,
      String transactionId,
      boolean mock
  ) {
    static WalletResult disabled() {
      return new WalletResult(false, "WALLET_DISABLED", 0, null, true);
    }
  }
}
