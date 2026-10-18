/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.infrastructure.repository;

import com.zxplat.framework.identity.domain.SecurityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 安全日志Repository
 */
@Repository
public interface SecurityLogRepository extends JpaRepository<SecurityLog, Long>, JpaSpecificationExecutor<SecurityLog> {

    /**
     * 根据用户ID查找安全日志
     */
    List<SecurityLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 根据操作类型查找安全日志
     */
    List<SecurityLog> findByActionOrderByCreatedAtDesc(String action);

    /**
     * 根据时间范围查找安全日志
     */
    List<SecurityLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);
}
