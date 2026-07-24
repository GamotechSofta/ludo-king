package com.ludo.backend.room;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RoomRepository extends MongoRepository<Room, String> {
  Optional<Room> findByRoomCode(String roomCode);

  List<Room> findByStatusAndFillDeadlineAtBefore(RoomStatus status, java.time.Instant before);

  List<Room> findByStatusAndMaxPlayersAndStakeTier(
      RoomStatus status, int maxPlayers, String stakeTier);
}
