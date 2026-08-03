package com.ludo.backend.platform.operator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Operator Balance API response (Integration Guide §5).
 * Game accepts the debit only when {@code status == true}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DebitResponse(boolean status) {
}
