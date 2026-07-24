package com.ludo.backend.user;

import org.springframework.stereotype.Service;

@Service
public class UserService {

  private final UserRepository userRepository;

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public User upsertOAuthUser(
      String provider,
      String providerId,
      String name,
      String email,
      String avatar
  ) {
    return userRepository
        .findByProviderAndProviderId(provider, providerId)
        .orElseGet(() -> userRepository.save(
            new User(provider, providerId, name, email, avatar)
        ));
  }
}
