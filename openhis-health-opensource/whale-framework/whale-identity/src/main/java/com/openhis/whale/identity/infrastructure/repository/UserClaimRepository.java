/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.infrastructure.repository;

import com.openhis.whale.identity.domain.UserClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户声明Repository
 */
@Repository
public interface UserClaimRepository extends JpaRepository<UserClaim, Long>, JpaSpecificationExecutor<UserClaim> {

    /**
     * 根据用户ID查找
     */
    List<UserClaim> findByUserId(Long userId);

    /**
     * 根据用户ID和声明类型查找
     */
    List<UserClaim> findByUserIdAndClaimType(Long userId, String claimType);

    /**
     * 根据用户ID、声明类型和声明值查找
     */
    Optional<UserClaim> findByUserIdAndClaimTypeAndClaimValue(Long userId, String claimType, String claimValue);

    /**
     * 删除用户的所有声明
     */
    void deleteByUserId(Long userId);

    /**
     * 删除用户的指定类型声明
     */
    void deleteByUserIdAndClaimType(Long userId, String claimType);

    /**
     * 删除用户声明
     */
    void deleteByUserIdAndClaimTypeAndClaimValue(Long userId, String claimType, String claimValue);
}
