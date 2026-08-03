package com.ludo.backend.platform.operator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Operator User Details API response (Integration Guide §4).
 * Wire shape only — unused until OperatorGatewayClient (Phase 2).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserDetailResponse(User user) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record User(
      @JsonProperty("user_id") String userId,
      String operatorId,
      double balance
  ) {
  }
}
