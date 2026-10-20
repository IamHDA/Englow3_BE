package com.englow3.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.user.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "learningPurposeIds", "targetSkills" })
    @org.springframework.data.jpa.repository.Query("select distinct u from User u where u.authProviderId=:authProviderId")
    Optional<User> findBootstrapByAuthProviderId(
            @org.springframework.data.repository.query.Param("authProviderId") UUID authProviderId);

    @org.springframework.data.jpa.repository.Query(value = "select * from users where role in ('ADMIN', 'STAFF') order by display_name, id limit 100", nativeQuery = true)
    java.util.List<User> backOfficeUsers();

    Optional<User> findByAuthProviderId(UUID authProviderId);
}
