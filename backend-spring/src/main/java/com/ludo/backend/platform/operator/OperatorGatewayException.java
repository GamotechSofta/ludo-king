package com.ludo.backend.platform.operator;

/** Base type for Operator Gateway HTTP client failures. */
public class OperatorGatewayException extends RuntimeException {

  public OperatorGatewayException(String message) {
    super(message);
  }

  public OperatorGatewayException(String message, Throwable cause) {
    super(message, cause);
  }
}
