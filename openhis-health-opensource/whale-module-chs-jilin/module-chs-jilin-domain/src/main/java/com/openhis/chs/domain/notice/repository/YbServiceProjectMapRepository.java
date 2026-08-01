/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.repository;

import com.openhis.chs.domain.notice.entity.YbServiceProjectMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 项目码映射仓储（M1 占位）。
 */
public interface YbServiceProjectMapRepository extends JpaRepository<YbServiceProjectMap, Long> {

    /**
     * 按租户与项目码取未删除映射。
     */
    Optional<YbServiceProjectMap> findByTenantIdAndProjectCodeAndIsDeletedFalse(
        Long tenantId, String projectCode);

    /**
     * 按租户批量取未删除映射（分批应用预加载）。
     */
    List<YbServiceProjectMap> findByTenantIdAndProjectCodeInAndIsDeletedFalse(
        Long tenantId, Collection<String> projectCodes);
}
