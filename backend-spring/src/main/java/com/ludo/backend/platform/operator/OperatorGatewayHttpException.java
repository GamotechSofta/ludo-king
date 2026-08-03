package com.ludo.backend.platform.operator;

/** Operator Gateway returned a non-2xx HTTP status. */
public class OperatorGatewayHttpException extends OperatorGatewayException {

  private final int statusCode;

  public OperatorGatewayHttpException(int statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }

  public OperatorGatewayHttpException(int statusCode, String message, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }
}
