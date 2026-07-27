package com.ludo.backend.admin;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "admin_settings")
public class AdminSettings {

  public static final String SINGLETON_ID = "default";

  @Id
  private String id = SINGLETON_ID;
  private double platformFeePerPlayer;
  private String currency = "INR";
  private Instant updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public double getPlatformFeePerPlayer() {
    return platformFeePerPlayer;
  }

  public void setPlatformFeePerPlayer(double platformFeePerPlayer) {
    this.platformFeePerPlayer = platformFeePerPlayer;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
