package com.ludo.backend.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * If REDIS_URL is blank, exclude Redis auto-config so local/dev still boots
 * without a Redis server. When REDIS_URL is set, Redis stays enabled.
 */
public class RedisOptionalEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  private static final String EXCLUDE_KEY = "spring.autoconfigure.exclude";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment,
      SpringApplication application
  ) {
    String redisUrl = environment.getProperty("REDIS_URL", "");
    if (StringUtils.hasText(redisUrl)) {
      return;
    }
    List<String> excludes = new ArrayList<>();
    String existing = environment.getProperty(EXCLUDE_KEY, "");
    if (StringUtils.hasText(existing)) {
      for (String part : existing.split(",")) {
        String trimmed = part.trim();
        if (!trimmed.isEmpty()) {
          excludes.add(trimmed);
        }
      }
    }
    excludes.add("org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration");
    excludes.add(
        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
    );
    excludes.add(
        "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
    );
    environment.getPropertySources().addFirst(
        new MapPropertySource(
            "ludoRedisOptional",
            Map.of(EXCLUDE_KEY, String.join(",", excludes))
        )
    );
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
