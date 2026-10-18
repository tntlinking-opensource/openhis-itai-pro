/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.caserecord.repository;

import com.zxplat.domain.mrms.caserecord.entity.CaseRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
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
     * 按租户 + 就诊主键集合批量加载未删除主档（列表 left join 回填病案号/上传状态）。
     *
     * <p>规模风险：调用方须控制 {@code encounterIds} 体量（典型为本页 200 条内），禁止全库 IN。</p>
     *
     * @param tenantId     租户
     * @param encounterIds 就诊主键集合
     * @return 命中的主档列表（无档就诊不在结果中）
     */
    List<CaseRecord> findByTenantIdAndEncounterIdInAndIsDeletedFalse(
            Long tenantId, Collection<Long> encounterIds);

    /**
     * 按租户 + 病案号精确查未删除主档（4103 运维按病案号解析 psn_no/setl_id）。
     *
     * @param tenantId 租户
     * @param medcasno 院内病案号
     * @return 主档
     */
    Optional<CaseRecord> findByTenantIdAndMedcasnoAndIsDeletedFalse(Long tenantId, String medcasno);

    /**
     * 统计患者历史住院次数（含当前）。
     *
     * @param tenantId 租户
     * @param patientId 患者主键
     * @return 条数
     */
    long countByTenantIdAndPatientIdAndIsDeletedFalse(Long tenantId, Long patientId);
}
