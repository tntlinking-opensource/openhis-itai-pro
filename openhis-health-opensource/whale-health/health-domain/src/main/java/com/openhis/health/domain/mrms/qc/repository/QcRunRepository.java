/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.qc.repository;

import com.openhis.health.domain.mrms.qc.entity.QcRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 质控执行批次仓储。
 */
public interface QcRunRepository extends JpaRepository<QcRun, Long> {

    /**
     * 按病案列出质控执行批次，完成时间倒序。
     *
     * @param caseRecordId 病案主档主键
     * @return 执行批次列表
     */
    List<QcRun> findByCaseRecordIdAndIsDeletedFalseOrderByFinishedAtDesc(Long caseRecordId);
}
