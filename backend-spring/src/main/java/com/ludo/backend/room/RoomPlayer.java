package com.ludo.backend.room;

public class RoomPlayer {
  private String userId;
  private String username;
  private String color;
  private boolean bot;
  private BotDifficulty botDifficulty;
  private int seatIndex;
  private ConnectionStatus connectionStatus = ConnectionStatus.CONNECTED;

  public RoomPlayer() {
  }

  public RoomPlayer(String userId, String username, String color, boolean bot, int seatIndex) {
    this.userId = userId;
    this.username = username;
    this.color = color;
    this.bot = bot;
    this.seatIndex = seatIndex;
    this.botDifficulty = bot ? BotDifficulty.MEDIUM : null;
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
}
