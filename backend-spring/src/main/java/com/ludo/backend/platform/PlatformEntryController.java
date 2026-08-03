package com.ludo.backend.platform;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Entry helpers for platform WebView. Prefer opening the frontend CRA with query params;
 * this also supports hitting the Spring host root with the same params (redirects to CLIENT_URL).
 */
@Controller
public class PlatformEntryController {

  @Value("${ludo.client-url:http://localhost:3043}")
  private String clientUrl;

  /**
   * Platform may point launchBaseUrl at the Spring service. We redirect to the SPA
   * with the same query string so the WebView loads the playable UI.
   */
  @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
  public void rootEntry(
      @RequestParam(value = "userId", required = false) String userId,
      @RequestParam Map<String, String> allParams,
      HttpServletResponse response
  ) throws IOException {
    if (userId == null || userId.isBlank()) {
      // Not a platform launch — JSON hint (API host has no SPA)
      response.setStatus(200);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write(
          "{\"ok\":true,\"service\":\"ludo-backend\",\"hint\":\"Open frontend CLIENT_URL with ?userId=... for platform play\"}"
      );
      return;
    }

    try {
      PlatformController.requireValidUserId(userId);
    } catch (Exception e) {
      writeErrorPage(response, 400, "Open this game from the platform app");
      return;
    }

    String frontend = firstClientOrigin();
    String qs = buildQuery(allParams);
    String target = frontend + "/?" + qs;
    response.setStatus(HttpServletResponse.SC_FOUND);
    response.setHeader("Location", target);
    response.setContentType(MediaType.TEXT_HTML_VALUE);
    response.getWriter().write(
        "<!DOCTYPE html><html><head>"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
            + "<meta http-equiv=\"refresh\" content=\"0;url=" + escape(target) + "\"/>"
            + "</head><body><p>Launching Ludo…</p></body></html>"
    );
  }

  @GetMapping(value = "/play", produces = MediaType.TEXT_HTML_VALUE)
  public void playEntry(
      @RequestParam(value = "userId", required = false) String userId,
      @RequestParam Map<String, String> allParams,
      HttpServletResponse response
  ) throws IOException {
    if (userId == null || userId.isBlank()) {
      writeErrorPage(response, 400, "Open this game from the platform app");
      return;
    }
    rootEntry(userId, allParams, response);
  }

  @GetMapping("/api/health")
  @ResponseBody
  public Map<String, Object> apiHealth() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("ok", true);
    body.put("status", "UP");
    return body;
  }

  private String firstClientOrigin() {
    String raw = clientUrl == null ? "http://localhost:3043" : clientUrl;
    String first = raw.split(",")[0].trim();
    return first.endsWith("/") ? first.substring(0, first.length() - 1) : first;
  }

  private static String buildQuery(Map<String, String> params) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> e : params.entrySet()) {
      if (e.getValue() == null) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append('&');
      }
      sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
          .append('=')
          .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
    }
    return sb.toString();
  }

  private static void writeErrorPage(HttpServletResponse response, int status, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.TEXT_HTML_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(
        "<!DOCTYPE html><html><head>"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
            + "<title>Ludo</title></head>"
            + "<body style=\"font-family:sans-serif;padding:24px;text-align:center;\">"
            + "<h1>" + escape(message) + "</h1>"
            + "<p>Missing or invalid launch parameters.</p></body></html>"
    );
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }
}
