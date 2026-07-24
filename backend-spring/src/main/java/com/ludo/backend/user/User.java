package com.ludo.backend.user;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
@CompoundIndex(name = "provider_providerId", def = "{'provider': 1, 'providerId': 1}", unique = true)
public class User {

  @Id
  private String id;
  private String provider;
  private String providerId;
  private String name;
  private String username;
  private String email;
  private String avatar;
  private String avatarId = "default";
  private int rating = 1000;
  private int level = 1;
  private long coins;
  private Stats stats = new Stats();

  @CreatedDate
  private Instant createdAt;

  public static class Stats {
    private int wins;
    private int losses;
    private int gamesPlayed;

    public int getWins() {
      return wins;
    }

    public void setWins(int wins) {
      this.wins = wins;
    }

    public int getLosses() {
      return losses;
    }

    public void setLosses(int losses) {
      this.losses = losses;
    }

    public int getGamesPlayed() {
      return gamesPlayed;
    }

    public void setGamesPlayed(int gamesPlayed) {
      this.gamesPlayed = gamesPlayed;
    }
  }

  public User() {
  }

  public User(String provider, String providerId, String name, String email, String avatar) {
    this.provider = provider;
    this.providerId = providerId;
    this.name = name;
    this.username = name;
    this.email = email;
    this.avatar = avatar;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getProviderId() {
    return providerId;
  }

  public void setProviderId(String providerId) {
    this.providerId = providerId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getAvatar() {
    return avatar;
  }

  public void setAvatar(String avatar) {
    this.avatar = avatar;
  }

  public String getAvatarId() {
    return avatarId;
  }

  public void setAvatarId(String avatarId) {
    this.avatarId = avatarId;
  }

  public int getRating() {
    return rating;
  }

  public void setRating(int rating) {
    this.rating = rating;
  }

  public int getLevel() {
    return level;
  }

  public void setLevel(int level) {
    this.level = level;
  }

  public long getCoins() {
    return coins;
  }

  public void setCoins(long coins) {
    this.coins = coins;
  }

  public Stats getStats() {
    return stats;
  }

  public void setStats(Stats stats) {
    this.stats = stats;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
