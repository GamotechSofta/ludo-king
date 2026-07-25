package com.ludo.backend.room;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface RoomRepository extends MongoRepository<Room, String> {
  Optional<Room> findByRoomCode(String roomCode);

  List<Room> findByStatusAndFillDeadlineAtBefore(RoomStatus status, java.time.Instant before);

  List<Room> findByStatusAndMaxPlayersAndStakeTier(
      RoomStatus status, int maxPlayers, String stakeTier);

  List<Room> findByStatusAndCountdownEndsAtBefore(RoomStatus status, java.time.Instant before);

  List<Room> findByStatusAndReconnectDeadlineAtBefore(
      RoomStatus status, java.time.Instant before);

  List<Room> findByStatus(RoomStatus status);

  @Query(
      "{ 'status': { $in: ['WAITING', 'READY', 'IN_PROGRESS', 'WAITING_RECONNECT'] },"
          + " 'players.userId': ?0 }"
  )
  List<Room> findActiveRoomsForUser(String userId);
}
