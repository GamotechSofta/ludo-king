package com.ludo.backend.user;

import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/guest")
public class GuestController {

  private final UserRepository userRepository;

  public GuestController(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public record GuestRequest(String username) {
  }

  @PostMapping
  public Map<String, Object> createGuest(@RequestBody GuestRequest req) {
    String name = req.username() == null || req.username().isBlank()
        ? "Guest" + (int) (Math.random() * 9000 + 1000)
        : req.username().trim();

    User user = new User();
    user.setProvider("guest");
    user.setProviderId(UUID.randomUUID().toString());
    user.setName(name);
    user.setUsername(name);
    user.setAvatarId("default");
    user.setRating(1000);
    user.setLevel(1);
    user.setCoins(0);
    user.setStats(new User.Stats());
    user = userRepository.save(user);

    return Map.of(
        "id", user.getId(),
        "username", user.getUsername(),
        "name", user.getName(),
        "rating", user.getRating(),
        "avatarId", user.getAvatarId()
    );
  }
}
