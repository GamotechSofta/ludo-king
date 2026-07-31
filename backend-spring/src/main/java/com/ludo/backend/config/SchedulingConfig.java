package com.ludo.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Isolate gameplay clocks from lobby work (LudoGame: dedicated lifecycle tick,
 * not one shared scheduler thread starving AFK timeouts under load).
 *
 * <p>Bean names must not collide with {@code @Component} classes
 * ({@code MatchmakingScheduler}, etc.).
 */
@Configuration
public class SchedulingConfig {

  @Bean(name = "timeoutTaskScheduler")
  public TaskScheduler timeoutTaskScheduler() {
    ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
    s.setPoolSize(2);
    s.setThreadNamePrefix("ludo-timeout-");
    s.setWaitForTasksToCompleteOnShutdown(true);
    s.setAwaitTerminationSeconds(5);
    s.initialize();
    return s;
  }

  @Bean(name = "matchmakingTaskScheduler")
  public TaskScheduler matchmakingTaskScheduler() {
    ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
    s.setPoolSize(2);
    s.setThreadNamePrefix("ludo-matchmaking-");
    s.setWaitForTasksToCompleteOnShutdown(true);
    s.setAwaitTerminationSeconds(5);
    s.initialize();
    return s;
  }
}
