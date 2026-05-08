package com.app.questr.repository;

import com.app.questr.model.entity.Badge;
import com.app.questr.model.enums.BadgeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BadgeRepository extends JpaRepository<Badge, UUID> {

    Optional<Badge> findByName(String name);

    List<Badge> findByBadgeType(BadgeType badgeType);

    boolean existsByName(String name);
}

