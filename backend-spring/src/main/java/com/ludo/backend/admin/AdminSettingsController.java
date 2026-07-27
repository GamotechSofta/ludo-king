package com.ludo.backend.admin;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
public class AdminSettingsController {

  private final AdminSettingsService settingsService;

  public AdminSettingsController(AdminSettingsService settingsService) {
    this.settingsService = settingsService;
  }

  @GetMapping
  public ResponseEntity<Map<String, Object>> get() {
    return ResponseEntity.ok(settingsService.getSettings());
  }

  @PutMapping
  public ResponseEntity<Map<String, Object>> put(@RequestBody UpdateBody body) {
    return ResponseEntity.ok(settingsService.updateSettings(body.platformFeePerPlayer()));
  }

  public record UpdateBody(Double platformFeePerPlayer) {}
}
