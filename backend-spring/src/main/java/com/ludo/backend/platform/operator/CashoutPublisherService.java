package com.ludo.backend.platform.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Publishes win credits to the operator cashout queue (Integration Guide §6).
 *
 * <p>Registered only when {@code ludo.wallet.mode=OPERATOR}. Publish waits for a
 * broker publisher confirm before returning success.
 */
@Service
@ConditionalOnProperty(prefix = "ludo.wallet", name = "mode", havingValue = "OPERATOR")
public class CashoutPublisherService {

  private static final Logger log = LoggerFactory.getLogger(CashoutPublisherService.class);

  private final RabbitTemplate rabbitTemplate;
  private final RabbitCashoutProperties props;
  private final ObjectMapper mapper = new ObjectMapper();

  public CashoutPublisherService(
      @Qualifier("rabbitCashoutRabbitTemplate") RabbitTemplate rabbitTemplate,
      RabbitCashoutProperties props
  ) {
    this.rabbitTemplate = rabbitTemplate;
    this.props = props;
    log.info(
        "cashout publisher READY exchange={} routingKey={} confirmTimeoutMs={}",
        props.exchange(),
        props.routingKey(),
        props.confirmTimeoutMs()
    );
  }

  /**
   * Validate, normalize amount to two decimals, publish persistent message, and
   * wait for broker confirm. Headers: {@code x-delay=0}, {@code x-retries=0}.
   */
  public void publish(CashoutMessage message) {
    validate(message);
    String amount = formatAmount(message.amount());
    CashoutMessage payload = new CashoutMessage(
        message.txnId().trim(),
        message.txnRefId().trim(),
        message.txnType(),
        amount,
        message.userId().trim(),
        message.gameId().trim(),
        message.description() == null ? "" : message.description().trim(),
        message.ip() == null ? "" : message.ip().trim(),
        message.operatorId().trim(),
        message.token().trim()
    );

    String exchange = props.exchange();
    String routingKey = props.routingKey();
    log.info(
        "cashout publish REQUEST txnId={} txnRefId={} userId={} amount={} exchange={} routingKey={}",
        payload.txnId(),
        payload.txnRefId(),
        payload.userId(),
        payload.amount(),
        exchange,
        routingKey
    );

    try {
      byte[] body = mapper.writeValueAsBytes(payload);
      Message amqpMessage = MessageBuilder.withBody(body)
          .setContentType(MessageProperties.CONTENT_TYPE_JSON)
          .setContentEncoding(StandardCharsets.UTF_8.name())
          .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
          .setHeader("x-delay", 0)
          .setHeader("x-retries", 0)
          .build();
      CorrelationData correlation = new CorrelationData(payload.txnId());
      rabbitTemplate.send(exchange, routingKey, amqpMessage, correlation);
      CorrelationData.Confirm confirm =
          correlation.getFuture().get(props.confirmTimeoutMs(), TimeUnit.MILLISECONDS);
      if (confirm == null) {
        throw new CashoutPublishException(
            "cashout publish confirm missing txnId=" + payload.txnId());
      }
      if (!confirm.ack()) {
        throw new CashoutPublishException(
            "cashout publish nack txnId="
                + payload.txnId()
                + " reason="
                + (confirm.reason() == null ? "" : confirm.reason()));
      }
      if (correlation.getReturned() != null) {
        throw new CashoutPublishException(
            "cashout publish unroutable txnId=" + payload.txnId());
      }
      log.info(
          "cashout publish OK (confirmed) txnId={} txnRefId={} userId={} amount={} exchange={} routingKey={}",
          payload.txnId(),
          payload.txnRefId(),
          payload.userId(),
          payload.amount(),
          exchange,
          routingKey
      );
    } catch (CashoutPublishException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          "cashout publish FAILED txnId={} txnRefId={} userId={} amount={} exchange={} routingKey={} err={}",
          payload.txnId(),
          payload.txnRefId(),
          payload.userId(),
          payload.amount(),
          exchange,
          routingKey,
          e.getMessage()
      );
      throw new CashoutPublishException(
          "cashout publish failed txnId=" + payload.txnId() + ": " + e.getMessage(), e);
    }
  }

  private static void validate(CashoutMessage message) {
    if (message == null) {
      throw new CashoutPublishException("cashout message is null");
    }
    requireText(message.txnId(), "txn_id");
    requireText(message.txnRefId(), "txn_ref_id");
    requireText(message.amount(), "amount");
    requireText(message.userId(), "user_id");
    requireText(message.gameId(), "game_id");
    requireText(message.operatorId(), "operatorId");
    requireText(message.token(), "token");
    if (message.txnType() != CashoutMessage.TXN_TYPE_CREDIT) {
      throw new CashoutPublishException(
          "cashout txn_type must be " + CashoutMessage.TXN_TYPE_CREDIT + " (credit)");
    }
    try {
      formatAmount(message.amount());
    } catch (CashoutPublishException e) {
      throw e;
    } catch (Exception e) {
      throw new CashoutPublishException("cashout amount invalid: " + message.amount(), e);
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new CashoutPublishException("cashout missing required field: " + field);
    }
  }

  /** Wire format: string with exactly two decimal places (e.g. {@code "700.00"}). */
  static String formatAmount(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new CashoutPublishException("cashout amount is blank");
    }
    try {
      return new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP).toPlainString();
    } catch (NumberFormatException e) {
      throw new CashoutPublishException("cashout amount not numeric: " + raw, e);
    }
  }
}
