/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.repository;

import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 病案主档仓储。
 */
public interface CaseRecordRepository
        extends JpaRepository<CaseRecord, Long>, JpaSpecificationExecutor<CaseRecord> {

    /**
     * 按主键加载未删除主档。
     *
     * @param id 主档主键
     * @return 主档
     */
    Optional<CaseRecord> findByIdAndIsDeletedFalse(Long id);

    /**
     * 按就诊查有效主档（幂等建档用）。
     *
     * @param tenantId 租户
     * @param encounterId 就诊主键
     * @return 主档
     */
    Optional<CaseRecord> findByTenantIdAndEncounterIdAndIsDeletedFalse(Long tenantId, Long encounterId);

    /**
     * 统计患者历史住院次数（含当前）。
     *
     * @param tenantId 租户
     * @param patientId 患者主键
     * @return 条数
     */
    long countByTenantIdAndPatientIdAndIsDeletedFalse(Long tenantId, Long patientId);
}
