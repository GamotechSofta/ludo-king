package com.ludo.backend.platform.operator;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * RabbitMQ infrastructure for cashout publish (Integration Guide §6).
 *
 * <p>Active only when {@code ludo.wallet.mode=OPERATOR}. Publisher is
 * {@link CashoutPublisherService}. ConnectionFactory is {@code @Primary} so it
 * wins over Boot's default localhost factory when both are present.
 */
@Configuration
@ConditionalOnProperty(prefix = "ludo.wallet", name = "mode", havingValue = "OPERATOR")
public class RabbitCashoutConfig {

  private static final Logger log = LoggerFactory.getLogger(RabbitCashoutConfig.class);

  @Bean
  @Primary
  ConnectionFactory rabbitCashoutConnectionFactory(RabbitCashoutProperties props) {
    if (!props.isConfigured()) {
      throw new IllegalStateException(
          "ludo.wallet.mode=OPERATOR requires ludo.rabbit-cashout.broker-uri (RABBITMQ_URI)");
    }
    CachingConnectionFactory factory = new CachingConnectionFactory();
    try {
      factory.setUri(props.brokerUri().trim());
    } catch (Exception e) {
      throw new IllegalStateException(
          "Invalid ludo.rabbit-cashout.broker-uri: " + props.brokerUri(), e);
    }
    factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
    factory.setPublisherReturns(true);
    log.info(
        "rabbit cashout ConnectionFactory READY exchange={} type={} delayedType={} queue={} routingKey={} confirms=CORRELATED",
        props.exchange(),
        props.exchangeType(),
        props.delayedType(),
        props.queue(),
        props.routingKey()
    );
    return factory;
  }

  @Bean
  RabbitAdmin rabbitCashoutAdmin(ConnectionFactory rabbitCashoutConnectionFactory) {
    return new RabbitAdmin(rabbitCashoutConnectionFactory);
  }

  @Bean
  RabbitTemplate rabbitCashoutRabbitTemplate(ConnectionFactory rabbitCashoutConnectionFactory) {
    RabbitTemplate template = new RabbitTemplate(rabbitCashoutConnectionFactory);
    template.setMandatory(true);
    template.setReturnsCallback(returned ->
        log.error(
            "rabbit cashout UNROUTABLE exchange={} routingKey={} replyCode={} replyText={}",
            returned.getExchange(),
            returned.getRoutingKey(),
            returned.getReplyCode(),
            returned.getReplyText()
        )
    );
    return template;
  }

  /**
   * Durable delayed-message exchange ({@code x-delayed-message} / direct).
   * Requires the {@code rabbitmq_delayed_message_exchange} broker plugin.
   */
  @Bean
  CustomExchange rabbitCashoutExchange(RabbitCashoutProperties props) {
    Map<String, Object> args = new HashMap<>();
    args.put("x-delayed-type", props.delayedType());
    return new CustomExchange(
        props.exchange(),
        props.exchangeType(),
        true,
        false,
        args
    );
  }

  @Bean
  Queue rabbitCashoutQueue(RabbitCashoutProperties props) {
    return QueueBuilder.durable(props.queue()).build();
  }

  @Bean
  Binding rabbitCashoutBinding(
      Queue rabbitCashoutQueue,
      CustomExchange rabbitCashoutExchange,
      RabbitCashoutProperties props
  ) {
    return BindingBuilder
        .bind(rabbitCashoutQueue)
        .to(rabbitCashoutExchange)
        .with(props.routingKey())
        .noargs();
  }
}
