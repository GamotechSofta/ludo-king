package com.ludo.backend.admin;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdminSettingsService {

  private final AdminSettingsRepository repository;
  private final double defaultPlatformFee;
  private final AtomicReference<CachedFee> feeCache = new AtomicReference<>();

  private record CachedFee(double fee, long expiresAtMs) {}

  public AdminSettingsService(
      AdminSettingsRepository repository,
      @Value("${ludo.admin.platform-fee-per-player:10}") double defaultPlatformFee
  ) {
    this.repository = repository;
    this.defaultPlatformFee = Math.max(0, defaultPlatformFee);
  }

  public Map<String, Object> getSettings() {
    AdminSettings s = load();
    return Map.of(
        "platformFeePerPlayer", s.getPlatformFeePerPlayer(),
        "currency", s.getCurrency() != null ? s.getCurrency() : "INR"
    );
  }

  /** Current house rake per paid seat (admin-configurable). Cached ~30s. */
  public double platformFeePerPlayer() {
    CachedFee cached = feeCache.get();
    long now = System.currentTimeMillis();
    if (cached != null && cached.expiresAtMs() > now) {
      return cached.fee();
    }
    double fee = Math.round(load().getPlatformFeePerPlayer() * 100.0) / 100.0;
    feeCache.set(new CachedFee(fee, now + 30_000));
    return fee;
  }

  public Map<String, Object> updateSettings(Double platformFeePerPlayer) {
    if (platformFeePerPlayer == null || platformFeePerPlayer < 0) {
      throw new IllegalArgumentException("platformFeePerPlayer must be >= 0");
    }
    AdminSettings s = load();
    s.setPlatformFeePerPlayer(platformFeePerPlayer);
    s.setUpdatedAt(Instant.now());
    repository.save(s);
    feeCache.set(new CachedFee(
        Math.round(platformFeePerPlayer * 100.0) / 100.0,
        System.currentTimeMillis() + 30_000
    ));
    return getSettings();
  }

  private AdminSettings load() {
    return repository.findById(AdminSettings.SINGLETON_ID).orElseGet(() -> {
      AdminSettings created = new AdminSettings();
      created.setId(AdminSettings.SINGLETON_ID);
      created.setPlatformFeePerPlayer(defaultPlatformFee);
      created.setCurrency("INR");
      created.setUpdatedAt(Instant.now());
      return repository.save(created);
    });
  }
}
