package com.ludo.backend.room;

public enum RoomStatus {
  /** Searching / seats not full. */
  WAITING,
  /** Room full — waiting for every human to press READY. */
  READY,
  /** Live match (PLAYING). */
  IN_PROGRESS,
  /** A seated player disconnected; seat held briefly. */
  WAITING_RECONNECT,
  /** Match over (FINISHED). */
  COMPLETED,
  ABANDONED
}
