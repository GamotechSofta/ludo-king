package com.ludo.backend.platform.operator;

/** Thrown when a cashout message cannot be published to RabbitMQ. */
public class CashoutPublishException extends RuntimeException {

  public CashoutPublishException(String message) {
    super(message);
  }

  public CashoutPublishException(String message, Throwable cause) {
    super(message, cause);
  }
}
