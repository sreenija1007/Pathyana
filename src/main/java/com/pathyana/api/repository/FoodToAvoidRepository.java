package com.pathyana.api.repository;

import com.pathyana.api.model.FoodToAvoid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodToAvoidRepository extends JpaRepository<FoodToAvoid, Long> {
    List<FoodToAvoid> findByUserId(Long userId);
}
