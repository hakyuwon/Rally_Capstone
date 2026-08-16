package com.gen.rally.repository;

import com.gen.rally.entity.MatchRequest;
import com.gen.rally.enums.GameType;
import com.gen.rally.enums.State;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {

    @Query("SELECT r FROM MatchRequest r " +
            "JOIN FETCH r.user u " +
            "WHERE u.userId != :userId " +
            "AND r.state = :state " +
            "AND r.gameDate = :gameDate " +
            "AND r.gameType = :gameType " +
            "AND r.skill BETWEEN :minSkill AND :maxSkill " +
            "AND r.startTime <= :myEndTime - 1 " +
            "AND r.endTime >= :myStartTime + 1")
    List<MatchRequest> findPotentialCandidates(
            @Param("userId") String userId,
            @Param("state") State state,
            @Param("gameDate") LocalDate gameDate,
            @Param("gameType") GameType gameType,
            @Param("minSkill") int minSkill,
            @Param("maxSkill") int maxSkill,
            @Param("myStartTime") int myStartTime,
            @Param("myEndTime") int myEndTime
    );

    @Query("""
    SELECT m FROM MatchRequest m
    WHERE m.user.userId = :userId
      AND m.gameDate = :gameDate
      AND (
           (m.startTime < :endTime AND m.endTime > :startTime)
          )
""")
    List<MatchRequest> findOverlappingRequests(String userId, LocalDate gameDate, int startTime, int endTime);

    @EntityGraph(attributePaths = {"user"})
    Optional<MatchRequest> findByRequestId(Long requestId);

    @Query("""
    select m from MatchRequest m
     where m.user.userId = :userId
       and m.state in :states
     order by m.createdAt desc
""")
    List<MatchRequest> findByUserAndStates(String userId, java.util.Collection<com.gen.rally.enums.State> states);
}
