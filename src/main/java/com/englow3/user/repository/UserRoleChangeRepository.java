package com.englow3.user.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.englow3.user.entity.UserRoleChange;

public interface UserRoleChangeRepository extends JpaRepository<UserRoleChange, UUID> {
    List<UserRoleChange> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);
}
