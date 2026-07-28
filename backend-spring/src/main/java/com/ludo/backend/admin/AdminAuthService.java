package com.ludo.backend.admin;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAuthService {

  private final String adminEmail;
  private final String adminPassword;
  private final String adminName;
  private final Map<String, Instant> tokens = new ConcurrentHashMap<>();
  private final long tokenTtlSeconds;

  public AdminAuthService(
      @Value("${ludo.admin.email:admin@ludo.local}") String adminEmail,
      @Value("${ludo.admin.password:admin123}") String adminPassword,
      @Value("${ludo.admin.name:Ludo King Admin}") String adminName,
      @Value("${ludo.admin.token-ttl-hours:24}") long tokenTtlHours
  ) {
    this.adminEmail = adminEmail.trim();
    this.adminPassword = adminPassword;
    this.adminName = adminName;
    this.tokenTtlSeconds = Math.max(1, tokenTtlHours) * 3600L;
  }

  public Map<String, Object> login(String email, String password) {
    if (email == null || password == null
        || !adminEmail.equalsIgnoreCase(email.trim())
        || !adminPassword.equals(password)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
    String token = UUID.randomUUID() + "." + UUID.randomUUID();
    tokens.put(token, Instant.now().plusSeconds(tokenTtlSeconds));
    return Map.of(
        "token", token,
        "admin", Map.of(
            "email", adminEmail,
            "name", adminName
        )
    );
  }

  public void logout(String token) {
    if (token != null && !token.isBlank()) {
      tokens.remove(token);
    }
  }

  public Map<String, Object> me(String token) {
    requireValid(token);
    return Map.of(
        "admin", Map.of(
            "email", adminEmail,
            "name", adminName
        )
    );
  }

  public void requireValid(String token) {
    if (token == null || token.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
    Instant exp = tokens.get(token);
    if (exp == null || Instant.now().isAfter(exp)) {
      tokens.remove(token);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
  }
}
