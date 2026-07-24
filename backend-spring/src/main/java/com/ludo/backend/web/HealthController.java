package com.ludo.backend.web;

import java.util.Map;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  private final MongoTemplate mongoTemplate;

  public HealthController(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    boolean mongoOk = false;
    try {
      mongoTemplate.getDb().runCommand(org.bson.Document.parse("{ ping: 1 }"));
      mongoOk = true;
    } catch (Exception ignored) {
      // reported below
    }

    return Map.of(
        "ok", mongoOk,
        "mongo", mongoOk,
        "redis", false,
        "engine", "spring-boot"
    );
  }
}
