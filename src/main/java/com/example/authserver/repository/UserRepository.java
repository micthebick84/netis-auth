package com.example.authserver.repository;

import com.example.authserver.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Modifying
    @Query("UPDATE User u SET u.failedAttempt = :failedAttempt WHERE u.username = :username")
    void updateFailedAttempt(@Param("failedAttempt") int failedAttempt, @Param("username") String username);

    @Modifying
    @Query("UPDATE User u SET u.accountNonLocked = :locked, u.lockTime = :lockTime WHERE u.username = :username")
    void lockUser(@Param("username") String username, @Param("locked") boolean locked, @Param("lockTime") java.time.LocalDateTime lockTime);

    @Modifying
    @Query("UPDATE User u SET u.accountNonLocked = true, u.lockTime = null, u.failedAttempt = 0 WHERE u.username = :username")
    void unlockUser(@Param("username") String username);
}
