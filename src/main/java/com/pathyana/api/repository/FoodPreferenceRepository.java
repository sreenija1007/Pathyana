package com.pathyana.api.repository;

import com.pathyana.api.model.FoodPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodPreferenceRepository extends JpaRepository<FoodPreference, Long> {
    List<FoodPreference> findByUserId(Long userId);
}
