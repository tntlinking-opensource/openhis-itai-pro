/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.repository;

import com.openhis.chs.domain.notice.entity.YbCatalogNoticeDoc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 医保文件通知文件头仓储；仅本聚合访问。
 */
public interface YbCatalogNoticeDocRepository
    extends JpaRepository<YbCatalogNoticeDoc, Long>, JpaSpecificationExecutor<YbCatalogNoticeDoc> {

    /**
     * 按租户与内容 hash 查找未删除文件头（导入去重）。
     *
     * @param tenantId       租户
     * @param sourceFileHash SHA-256
     * @return 已存在文件头
     */
    Optional<YbCatalogNoticeDoc> findByTenantIdAndSourceFileHashAndIsDeletedFalse(
        Long tenantId, String sourceFileHash);

    /**
     * 按主键与租户取未删除文件头。
     *
     * @param id       文件头主键
     * @param tenantId 租户
     * @return 文件头
     */
    Optional<YbCatalogNoticeDoc> findByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);
}
