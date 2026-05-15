package com.pathyana.api.repository;

import com.pathyana.api.model.HealthCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HealthConditionRepository extends JpaRepository<HealthCondition, Long> {
    List<HealthCondition> findByUserId(Long userId);
}
