package com.app.questr.repository;

import com.app.questr.model.entity.UserStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserStatsRepository extends JpaRepository<UserStats, UUID> {

    Optional<UserStats> findByUserId(UUID userId);

    /** Used by the midnight streak-update cron to iterate all active users. */
    List<UserStats> findAllByLastActivityDateIsNotNull();
}

