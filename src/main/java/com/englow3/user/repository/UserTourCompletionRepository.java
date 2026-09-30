package com.englow3.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.user.entity.Role;
import com.englow3.user.entity.UserTourCompletion;

public interface UserTourCompletionRepository extends JpaRepository<UserTourCompletion, UUID> {

    boolean existsByUserIdAndRoleAndVersion(UUID userId, Role role, int version);

    @Modifying
    @Query(value = """
            insert into user_tour_completions (id, user_id, role, version)
            values (:id, :userId, :role, :version)
            on conflict (user_id, role, version) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId, @Param("role") String role,
            @Param("version") int version);
}
