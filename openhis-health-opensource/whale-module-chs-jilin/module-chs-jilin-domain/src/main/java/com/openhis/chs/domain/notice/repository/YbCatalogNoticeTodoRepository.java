/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.repository;

import com.openhis.chs.domain.notice.entity.YbCatalogNoticeTodo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 通知待办仓储。
 */
public interface YbCatalogNoticeTodoRepository
    extends JpaRepository<YbCatalogNoticeTodo, Long>, JpaSpecificationExecutor<YbCatalogNoticeTodo> {

    Optional<YbCatalogNoticeTodo> findByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    Optional<YbCatalogNoticeTodo>
        findFirstByTenantIdAndTodoTypeAndHilistCodeAndStatusAndIsDeletedFalse(
            Long tenantId, String todoType, String hilistCode, String status);

    long countByTenantIdAndTodoTypeAndStatusAndIsDeletedFalse(
        Long tenantId, String todoType, String status);
}
