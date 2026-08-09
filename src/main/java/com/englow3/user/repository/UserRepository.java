package com.englow3.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.user.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByAuthProviderId(UUID authProviderId);
}
