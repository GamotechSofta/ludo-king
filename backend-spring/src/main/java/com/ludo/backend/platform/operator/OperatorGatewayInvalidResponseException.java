package com.ludo.backend.platform.operator;

/** Response body missing, unparseable, or missing required fields. */
public class OperatorGatewayInvalidResponseException extends OperatorGatewayException {

  public OperatorGatewayInvalidResponseException(String message) {
    super(message);
  }

  public OperatorGatewayInvalidResponseException(String message, Throwable cause) {
    super(message, cause);
  }
}
