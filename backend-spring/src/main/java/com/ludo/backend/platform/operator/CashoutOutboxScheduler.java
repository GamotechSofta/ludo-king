package com.ludo.backend.platform.operator;

import com.ludo.backend.platform.wallet.MatchEconomyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains OPERATOR cashout outbox rows stuck in {@code PENDING}.
 * Does not alter game rules or settlement math — only retries broker publish.
 */
@Component
@ConditionalOnProperty(prefix = "ludo.wallet", name = "mode", havingValue = "OPERATOR")
public class CashoutOutboxScheduler {

  private static final Logger log = LoggerFactory.getLogger(CashoutOutboxScheduler.class);

  private final MatchEconomyService matchEconomy;

  public CashoutOutboxScheduler(MatchEconomyService matchEconomy) {
    this.matchEconomy = matchEconomy;
  }

  @Scheduled(fixedDelayString = "${ludo.rabbit-cashout.retry-delay-ms:15000}")
  public void drainPendingCashouts() {
    int n = matchEconomy.retryPendingCashouts(50);
    if (n > 0) {
      log.info("cashout outbox drained attempts={}", n);
    }
  }
}
