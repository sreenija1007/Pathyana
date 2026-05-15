package com.pathyana.api.repository;

import com.pathyana.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data builds the implementation at runtime. We only declare the methods we need.
 *
 * findByEmail is critical for login and signup uniqueness checks.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByVerificationToken(String token);
}
