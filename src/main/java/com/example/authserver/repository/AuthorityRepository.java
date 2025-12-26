package com.example.authserver.repository;

import com.example.authserver.entity.Authority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuthorityRepository extends JpaRepository<Authority, Long> {

    List<Authority> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
