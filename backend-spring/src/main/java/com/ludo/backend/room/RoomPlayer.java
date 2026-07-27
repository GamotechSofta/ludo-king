package com.ludo.backend.room;

import java.time.Instant;

public class RoomPlayer {
  private String userId;
  private String username;
  private String avatar;
  private int rating;
  private String color;
  private boolean bot;
  private BotDifficulty botDifficulty;
  private int seatIndex;
  private ConnectionStatus connectionStatus = ConnectionStatus.CONNECTED;
  private boolean ready;
  private Instant disconnectedAt;
  private String socketId;

  public RoomPlayer() {
  }

  public RoomPlayer(String userId, String username, String color, boolean bot, int seatIndex) {
    this.userId = userId;
    this.username = username;
    this.color = color;
    this.bot = bot;
    this.seatIndex = seatIndex;
    this.botDifficulty = bot ? BotDifficulty.HARD : null;
    this.ready = bot;
    this.avatar = bot ? "bot" : "default";
    this.rating = 1000;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getAvatar() {
    return avatar;
  }

  public void setAvatar(String avatar) {
    this.avatar = avatar;
  }

  public int getRating() {
    return rating;
  }

  public void setRating(int rating) {
    this.rating = rating;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public boolean isBot() {
    return bot;
  }

  public void setBot(boolean bot) {
    this.bot = bot;
  }

  public BotDifficulty getBotDifficulty() {
    return botDifficulty;
  }

  public void setBotDifficulty(BotDifficulty botDifficulty) {
    this.botDifficulty = botDifficulty;
  }

  public int getSeatIndex() {
    return seatIndex;
  }

  public void setSeatIndex(int seatIndex) {
    this.seatIndex = seatIndex;
  }

  public ConnectionStatus getConnectionStatus() {
    return connectionStatus;
  }

  public void setConnectionStatus(ConnectionStatus connectionStatus) {
    this.connectionStatus = connectionStatus;
  }

  public boolean isReady() {
    return ready;
  }

  public void setReady(boolean ready) {
    this.ready = ready;
  }

  public Instant getDisconnectedAt() {
    return disconnectedAt;
  }

  public void setDisconnectedAt(Instant disconnectedAt) {
    this.disconnectedAt = disconnectedAt;
  }

  public String getSocketId() {
    return socketId;
  }

  public void setSocketId(String socketId) {
    this.socketId = socketId;
  }
}
