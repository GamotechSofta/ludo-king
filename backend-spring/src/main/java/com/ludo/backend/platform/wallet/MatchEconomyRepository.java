package com.ludo.backend.platform.wallet;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MatchEconomyRepository extends MongoRepository<MatchEconomyEntry, String> {
  Optional<MatchEconomyEntry> findByMatchIdAndUserId(String matchId, String userId);

  List<MatchEconomyEntry> findByMatchId(String matchId);

  List<MatchEconomyEntry> findByUserId(String userId);
}
