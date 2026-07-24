package com.ludo.backend.security;

import com.ludo.backend.config.LudoProperties;
import com.ludo.backend.user.User;
import com.ludo.backend.user.UserService;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final LudoProperties properties;
  private final UserService userService;

  public SecurityConfig(LudoProperties properties, UserService userService) {
    this.properties = properties;
    this.userService = userService;
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ObjectProvider<ClientRegistrationRepository> clients
  ) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/health",
                "/auth/options",
                "/api/me",
                "/api/logout",
                "/ws/**",
                "/oauth2/**",
                "/login/oauth2/**"
            ).permitAll()
            .anyRequest().permitAll()
        )
        .exceptionHandling(ex ->
            ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .logout(logout -> logout
            .logoutUrl("/api/logout")
            .logoutSuccessHandler((request, response, authentication) -> {
              response.setStatus(HttpStatus.OK.value());
              response.setContentType("application/json");
              response.getWriter().write("{\"ok\":true}");
            })
            .invalidateHttpSession(true)
            .deleteCookies("SESSION", "JSESSIONID")
        );

    if (clients.getIfAvailable() != null) {
      http.oauth2Login(oauth -> oauth
          .userInfoEndpoint(userInfo -> userInfo.userService(oAuth2UserService()))
          .defaultSuccessUrl(properties.primaryClientUrl(), true)
          .failureUrl(properties.primaryClientUrl())
      );
    }

    return http.build();
  }

  private OAuth2UserService<OAuth2UserRequest, OAuth2User> oAuth2UserService() {
    DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    return request -> {
      OAuth2User oauthUser = delegate.loadUser(request);
      String registrationId = request.getClientRegistration().getRegistrationId();
      Map<String, Object> attrs = oauthUser.getAttributes();

      String providerId;
      String name;
      String email = null;
      String avatar = null;

      if ("google".equals(registrationId)) {
        providerId = String.valueOf(attrs.get("sub"));
        name = String.valueOf(attrs.getOrDefault("name", "google-user"));
        email = attrs.get("email") != null ? String.valueOf(attrs.get("email")) : null;
        avatar = attrs.get("picture") != null ? String.valueOf(attrs.get("picture")) : null;
      } else {
        providerId = String.valueOf(attrs.get("id"));
        name = String.valueOf(attrs.getOrDefault("login", attrs.getOrDefault("name", "github-user")));
        email = attrs.get("email") != null ? String.valueOf(attrs.get("email")) : null;
        avatar = attrs.get("avatar_url") != null ? String.valueOf(attrs.get("avatar_url")) : null;
      }

      User user = userService.upsertOAuthUser(registrationId, providerId, name, email, avatar);

      return new DefaultOAuth2User(
          List.of(new SimpleGrantedAuthority("ROLE_USER")),
          Map.of(
              "id", user.getId(),
              "name", user.getName(),
              "email", user.getEmail() != null ? user.getEmail() : "",
              "avatar", user.getAvatar() != null ? user.getAvatar() : "",
              "provider", user.getProvider()
          ),
          "id"
      );
    };
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(properties.allowedClientOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
