package com.ludo.backend.admin;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/profit-loss")
public class AdminProfitLossController {

  private final AdminProfitLossService service;

  public AdminProfitLossController(AdminProfitLossService service) {
    this.service = service;
  }

  @GetMapping("/summary")
  public ResponseEntity<Map<String, Object>> summary(
      @RequestParam(required = false) Integer players,
      @RequestParam(required = false) String operatorId
  ) {
    return ResponseEntity.ok(service.summary(players, operatorId));
  }

  @GetMapping("/games")
  public ResponseEntity<Map<String, Object>> games(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) Integer players,
      @RequestParam(required = false) String operatorId
  ) {
    return ResponseEntity.ok(service.games(page, limit, players, operatorId));
  }

  @GetMapping("/users")
  public ResponseEntity<Map<String, Object>> users(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) Integer players,
      @RequestParam(required = false) String operatorId
  ) {
    return ResponseEntity.ok(service.users(page, limit, players, operatorId));
  }
}
