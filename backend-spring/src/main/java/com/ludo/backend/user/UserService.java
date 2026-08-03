package com.ludo.backend.user;

import java.util.Optional;
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

  /**
   * Persist platform player display name (platform userId) so admin P&amp;L can show real names.
   */
  public User upsertPlatformProfile(String userId, String displayName) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId required");
    }
    String label = cleanDisplayName(displayName, userId);
    Optional<User> existing = userRepository.findById(userId);
    if (existing.isEmpty()) {
      existing = userRepository.findByProviderAndProviderId("platform", userId);
    }
    User user = existing.orElseGet(() -> {
      User u = new User();
      u.setId(userId);
      u.setProvider("platform");
      u.setProviderId(userId);
      u.setAvatarId("default");
      u.setRating(1000);
      u.setLevel(1);
      u.setCoins(0);
      u.setStats(new User.Stats());
      return u;
    });
    // Prefer a non-generic incoming name; keep prior real name if launch sends "Player"
    if (!isGenericPlayerLabel(label) || isGenericPlayerLabel(user.getUsername())) {
      user.setName(label);
      user.setUsername(label);
    }
    return userRepository.save(user);
  }

  public Optional<User> findById(String userId) {
    if (userId == null || userId.isBlank()) {
      return Optional.empty();
    }
    return userRepository.findById(userId)
        .or(() -> userRepository.findByProviderAndProviderId("platform", userId));
  }

  public String resolveDisplayName(String userId, String fallback) {
    Optional<User> user = findById(userId);
    if (user.isPresent()) {
      String fromUser = firstUseful(user.get().getName(), user.get().getUsername());
      if (fromUser != null) {
        return fromUser;
      }
    }
    if (fallback != null && !fallback.isBlank() && !isGenericPlayerLabel(fallback)) {
      return fallback.trim();
    }
    return cleanDisplayName(fallback, userId);
  }

  private static String firstUseful(String... names) {
    for (String n : names) {
      if (n != null && !n.isBlank() && !isGenericPlayerLabel(n)) {
        return n.trim();
      }
    }
    return null;
  }

  public static boolean isGenericPlayerLabel(String name) {
    if (name == null || name.isBlank()) {
      return true;
    }
    return name.trim().matches("(?i)Player(?:\\s*\\d+)?");
  }

  private static String cleanDisplayName(String displayName, String userId) {
    if (displayName != null && !displayName.isBlank() && !isGenericPlayerLabel(displayName)) {
      String t = displayName.trim();
      return t.length() > 64 ? t.substring(0, 64) : t;
    }
    if (userId != null && userId.length() >= 4) {
      return "Player " + userId.substring(userId.length() - 4).toUpperCase();
    }
    return "Player";
  }
}
