package com.ludo.backend.platform.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Operator Gateway HTTP client (Integration Guide §4–§5).
 *
 * <p>Registered only when {@code ludo.wallet.mode=OPERATOR}. Not wired into economy
 * services yet — callers will be added in a later phase.
 */
@Service
@ConditionalOnProperty(prefix = "ludo.wallet", name = "mode", havingValue = "OPERATOR")
public class OperatorGatewayClient implements WalletGateway {

  private static final Logger log = LoggerFactory.getLogger(OperatorGatewayClient.class);
  private static final int LOG_BODY_CHARS = 200;

  private final OperatorProperties props;
  private final RestTemplate restTemplate;
  private final ObjectMapper mapper = new ObjectMapper();

  public OperatorGatewayClient(OperatorProperties props, RestTemplateBuilder builder) {
    this.props = props;
    Duration connect = Duration.ofMillis(props.connectTimeoutMs());
    Duration read = Duration.ofMillis(props.readTimeoutMs());
    this.restTemplate = builder
        .setConnectTimeout(connect)
        .setReadTimeout(read)
        .build();
    log.info(
        "operator gateway client READY baseUrl={} userDetailPath={} balancePath={} connectTimeoutMs={} readTimeoutMs={}",
        props.base(),
        props.userDetailPath(),
        props.balancePath(),
        props.connectTimeoutMs(),
        props.readTimeoutMs()
    );
  }

  @Override
  public UserDetailResponse fetchUserDetail(String token) {
    String normalizedToken = requireToken(token);
    String url = urlFor(props.userDetailPath());
    HttpHeaders headers = tokenHeaders(normalizedToken);
    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

    log.info("operator userDetail REQUEST url={}", url);
    String body = exchange(url, HttpMethod.GET, new HttpEntity<>(headers), "userDetail");
    UserDetailResponse parsed = readJson(body, UserDetailResponse.class, "userDetail", url);
    if (parsed == null || parsed.user() == null) {
      throw new OperatorGatewayInvalidResponseException(
          "operator userDetail missing user object url=" + url);
    }
    UserDetailResponse.User user = parsed.user();
    if (user.userId() == null || user.userId().isBlank()) {
      throw new OperatorGatewayInvalidResponseException(
          "operator userDetail missing user_id url=" + url);
    }
    if (user.operatorId() == null || user.operatorId().isBlank()) {
      throw new OperatorGatewayInvalidResponseException(
          "operator userDetail missing operatorId url=" + url);
    }
    log.info(
        "operator userDetail OK url={} userId={} operatorId={} balance={}",
        url,
        user.userId(),
        user.operatorId(),
        user.balance()
    );
    return parsed;
  }

  @Override
  public DebitResponse debit(String token, DebitRequest request) {
    if (request == null) {
      throw new OperatorGatewayInvalidResponseException("operator debit request is null");
    }
    String normalizedToken = requireToken(token);
    String url = urlFor(props.balancePath());
    HttpHeaders headers = tokenHeaders(normalizedToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

    log.info(
        "operator debit REQUEST url={} txnId={} userId={} amount={} txnType={} gameId={} betId={}",
        url,
        request.txnId(),
        request.userId(),
        request.amount(),
        request.txnType(),
        request.gameId(),
        request.betId()
    );
    String body = exchange(url, HttpMethod.POST, new HttpEntity<>(request, headers), "debit");
    DebitResponse parsed = readJson(body, DebitResponse.class, "debit", url);
    if (parsed == null) {
      throw new OperatorGatewayInvalidResponseException(
          "operator debit empty/unparseable response url=" + url);
    }
    if (parsed.status()) {
      log.info("operator debit OK url={} txnId={}", url, request.txnId());
    } else {
      log.warn("operator debit DECLINED url={} txnId={}", url, request.txnId());
    }
    return parsed;
  }

  private String exchange(String url, HttpMethod method, HttpEntity<?> entity, String op) {
    try {
      ResponseEntity<String> res = restTemplate.exchange(url, method, entity, String.class);
      int status = res.getStatusCode().value();
      if (!res.getStatusCode().is2xxSuccessful()) {
        log.error(
            "operator {} HTTP ERROR url={} status={} body={}",
            op, url, status, trim(res.getBody())
        );
        throw new OperatorGatewayHttpException(
            status, "operator " + op + " HTTP " + status + " url=" + url);
      }
      return res.getBody() == null ? "" : res.getBody();
    } catch (OperatorGatewayException e) {
      throw e;
    } catch (HttpStatusCodeException e) {
      int status = e.getStatusCode().value();
      log.error(
          "operator {} HTTP ERROR url={} status={} body={}",
          op, url, status, trim(e.getResponseBodyAsString())
      );
      throw new OperatorGatewayHttpException(
          status, "operator " + op + " HTTP " + status + " url=" + url, e);
    } catch (ResourceAccessException e) {
      log.error("operator {} TIMEOUT/UNREACHABLE url={} err={}", op, url, e.getMessage());
      throw new OperatorGatewayTimeoutException(
          "operator " + op + " timed out or unreachable url=" + url, e);
    } catch (RestClientException e) {
      log.error("operator {} ERROR url={} err={}", op, url, e.getMessage());
      throw new OperatorGatewayInvalidResponseException(
          "operator " + op + " call failed url=" + url + ": " + e.getMessage(), e);
    }
  }

  private <T> T readJson(String raw, Class<T> type, String op, String url) {
    String body = raw == null ? "" : raw.trim();
    if (body.isEmpty()) {
      log.error("operator {} INVALID RESPONSE empty body url={}", op, url);
      throw new OperatorGatewayInvalidResponseException(
          "operator " + op + " empty body url=" + url);
    }
    try {
      return mapper.readValue(body, type);
    } catch (Exception e) {
      log.error(
          "operator {} INVALID RESPONSE unparseable url={} bodyPrefix={}",
          op, url, trim(body)
      );
      throw new OperatorGatewayInvalidResponseException(
          "operator " + op + " unparseable JSON url=" + url, e);
    }
  }

  private String urlFor(String path) {
    String base = props.base();
    if (base.isBlank()) {
      throw new OperatorGatewayInvalidResponseException(
          "operator base URL blank — set OPERATOR_BASE_URL when wallet.mode=OPERATOR");
    }
    String p = path == null ? "" : path.trim();
    if (!p.startsWith("/")) {
      p = "/" + p;
    }
    return base + p;
  }

  private static String requireToken(String token) {
    if (token == null || token.isBlank()) {
      throw new OperatorGatewayInvalidResponseException("operator token is required");
    }
    return token.trim();
  }

  private static HttpHeaders tokenHeaders(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("token", token);
    return headers;
  }

  private static String trim(String value) {
    if (value == null) {
      return "";
    }
    return value.length() <= LOG_BODY_CHARS ? value : value.substring(0, LOG_BODY_CHARS) + "…";
  }
}
