/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.upload.repository;

import com.openhis.health.domain.mrms.upload.entity.UploadLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 4401 上传日志仓储。
 */
public interface UploadLogRepository
        extends JpaRepository<UploadLog, Long>, JpaSpecificationExecutor<UploadLog> {

    /**
     * 按主键加载未软删上传日志。
     *
     * @param id 上传日志主键
     * @return 存在则含实体，否则 empty
     */
    Optional<UploadLog> findByIdAndIsDeletedFalse(Long id);

    /**
     * 按病案列出上传日志，创建时间倒序。
     *
     * @param caseRecordId 病案主档主键
     * @return 上传日志列表
     */
    List<UploadLog> findByCaseRecordIdAndIsDeletedFalseOrderByCreatedAtDesc(Long caseRecordId);
}
