package com.ludo.backend.platform.operator;

/** Connect/read timeout (or unreachable host) talking to the Operator Gateway. */
public class OperatorGatewayTimeoutException extends OperatorGatewayException {

  public OperatorGatewayTimeoutException(String message) {
    super(message);
  }

  public OperatorGatewayTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
