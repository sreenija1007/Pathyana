package com.pathyana.api.repository;

import com.pathyana.api.model.Allergy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AllergyRepository extends JpaRepository<Allergy, Long> {
    List<Allergy> findByUserId(Long userId);
}
