package com.ludo.backend.config;

import com.ludo.backend.config.LudoProperties.OAuthProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.session.data.mongo.config.annotation.web.http.EnableMongoHttpSession;

@Configuration
@EnableMongoAuditing
@EnableMongoHttpSession
public class AppConfig {

  @Bean
  @ConditionalOnProperty(prefix = "ludo", name = "oauth-enabled", havingValue = "true")
  ClientRegistrationRepository clientRegistrationRepository(LudoProperties properties) {
    List<ClientRegistration> registrations = new ArrayList<>();

    OAuthProperties google = properties.google();
    if (google.isEnabled()) {
      registrations.add(
          ClientRegistration.withRegistrationId("google")
              .clientId(google.clientId())
              .clientSecret(google.clientSecret())
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid", "profile", "email")
              .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
              .tokenUri("https://www.googleapis.com/oauth2/v4/token")
              .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
              .userNameAttributeName(IdTokenClaimNames.SUB)
              .clientName("Google")
              .build()
      );
    }

    OAuthProperties github = properties.github();
    if (github.isEnabled()) {
      registrations.add(
          ClientRegistration.withRegistrationId("github")
              .clientId(github.clientId())
              .clientSecret(github.clientSecret())
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("read:user", "user:email")
              .authorizationUri("https://github.com/login/oauth/authorize")
              .tokenUri("https://github.com/login/oauth/access_token")
              .userInfoUri("https://api.github.com/user")
              .userNameAttributeName("id")
              .clientName("GitHub")
              .build()
      );
    }

    if (registrations.isEmpty()) {
      throw new IllegalStateException("ludo.oauth-enabled=true but no OAuth providers configured");
    }

    return new InMemoryClientRegistrationRepository(registrations);
  }
}
