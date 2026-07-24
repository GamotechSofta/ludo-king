package com.ludo.backend.web;

import com.ludo.backend.config.LudoProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

  private final LudoProperties properties;

  public AuthController(LudoProperties properties) {
    this.properties = properties;
  }

  @GetMapping("/api/me")
  public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal OAuth2User principal) {
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("authenticated", false));
    }

    Map<String, Object> user = new LinkedHashMap<>();
    user.put("id", principal.getAttribute("id"));
    user.put("name", principal.getAttribute("name"));
    user.put("email", principal.getAttribute("email"));
    user.put("avatar", principal.getAttribute("avatar"));
    user.put("provider", principal.getAttribute("provider"));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("authenticated", true);
    body.put("user", user);
    return ResponseEntity.ok(body);
  }

  @GetMapping("/auth/options")
  public Map<String, Boolean> authOptions() {
    return Map.of(
        "google", properties.google().isEnabled(),
        "github", properties.github().isEnabled()
    );
  }
}
