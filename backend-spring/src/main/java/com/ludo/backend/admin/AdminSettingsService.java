package com.ludo.backend.admin;

import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdminSettingsService {

  private final AdminSettingsRepository repository;
  private final double defaultFee;

  public AdminSettingsService(
      AdminSettingsRepository repository,
      @Value("${ludo.wallet.entry-fee:10}") double defaultFee
  ) {
    this.repository = repository;
    this.defaultFee = defaultFee;
  }

  public Map<String, Object> getSettings() {
    AdminSettings s = load();
    return Map.of(
        "platformFeePerPlayer", s.getPlatformFeePerPlayer(),
        "currency", s.getCurrency() != null ? s.getCurrency() : "INR"
    );
  }

  public Map<String, Object> updateSettings(Double platformFeePerPlayer) {
    if (platformFeePerPlayer == null || platformFeePerPlayer < 0) {
      throw new IllegalArgumentException("platformFeePerPlayer must be >= 0");
    }
    AdminSettings s = load();
    s.setPlatformFeePerPlayer(platformFeePerPlayer);
    s.setUpdatedAt(Instant.now());
    repository.save(s);
    return getSettings();
  }

  private AdminSettings load() {
    return repository.findById(AdminSettings.SINGLETON_ID).orElseGet(() -> {
      AdminSettings created = new AdminSettings();
      created.setId(AdminSettings.SINGLETON_ID);
      created.setPlatformFeePerPlayer(defaultFee);
      created.setCurrency("INR");
      created.setUpdatedAt(Instant.now());
      return repository.save(created);
    });
  }
}
