package com.ludo.backend.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Wires RedisGameBridge only when Redis auto-config created a connection factory.
 * Avoids nested @Configuration inside a conditional @Component (startup crash on Render).
 */
@Configuration
@ConditionalOnBean(RedisConnectionFactory.class)
public class RedisGameBridgeConfig {

  @Bean
  RedisGameBridge redisGameBridge(
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      SimpMessagingTemplate messagingTemplate
  ) {
    return new RedisGameBridge(redis, objectMapper, messagingTemplate);
  }

  @Bean
  RedisMessageListenerContainer ludoRedisGameListenerContainer(
      RedisConnectionFactory factory,
      RedisGameBridge bridge
  ) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(factory);
    container.addMessageListener(bridge, new ChannelTopic(RedisGameBridge.CHANNEL));
    return container;
  }
}
