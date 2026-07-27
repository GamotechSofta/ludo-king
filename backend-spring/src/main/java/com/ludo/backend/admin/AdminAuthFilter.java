package com.ludo.backend.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

/**
 * Bearer-token gate for /api/v1/admin/** (except login).
 * Keeps existing Spring Security permitAll for the game APIs.
 */
@Component
@Order(20)
public class AdminAuthFilter extends OncePerRequestFilter {

  private final AdminAuthService authService;

  public AdminAuthFilter(AdminAuthService authService) {
    this.authService = authService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // Preflight must pass through to Spring CORS (no Bearer on OPTIONS)
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String path = request.getRequestURI();
    if (path == null || !path.startsWith("/api/v1/admin/")) {
      return true;
    }
    return path.equals("/api/v1/admin/auth/login");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    String token = null;
    if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      token = header.substring(7).trim();
    }
    try {
      authService.requireValid(token);
      filterChain.doFilter(request, response);
    } catch (ResponseStatusException ex) {
      response.setStatus(ex.getStatusCode().value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write("{\"message\":\"" + ex.getReason() + "\"}");
    }
  }
}
