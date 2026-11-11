package com.englow3.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.user.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    @org.springframework.data.jpa.repository.Query("select u.role from User u where u.authProviderId=:id")
    Optional<com.englow3.user.entity.Role> roleByAuthProviderId(
            @org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id=:id")
    Optional<User> lockById(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Query(value = "select cast(pg_advisory_xact_lock(8723149202) as text)", nativeQuery = true)
    String lockRoleChanges();

    @org.springframework.data.jpa.repository.Query("select u from User u where (:role is null or u.role=:role) and (lower(u.email) like :term or lower(u.displayName) like :term) order by u.displayName,u.id")
    org.springframework.data.domain.Page<User> searchForAdmin(
            @org.springframework.data.repository.query.Param("term") String term,
            @org.springframework.data.repository.query.Param("role") com.englow3.user.entity.Role role,
            org.springframework.data.domain.Pageable page);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "learningPurposeIds", "targetSkills" })
    @org.springframework.data.jpa.repository.Query("select distinct u from User u where u.authProviderId=:authProviderId")
    Optional<User> findBootstrapByAuthProviderId(
            @org.springframework.data.repository.query.Param("authProviderId") UUID authProviderId);

    @org.springframework.data.jpa.repository.Query(value = "select * from users where role in ('ADMIN', 'STAFF') order by display_name, id limit 100", nativeQuery = true)
    java.util.List<User> backOfficeUsers();

    Optional<User> findByAuthProviderId(UUID authProviderId);
}
