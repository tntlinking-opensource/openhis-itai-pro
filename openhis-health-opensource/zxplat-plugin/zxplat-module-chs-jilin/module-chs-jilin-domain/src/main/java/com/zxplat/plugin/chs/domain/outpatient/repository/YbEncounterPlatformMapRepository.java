/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.outpatient.repository;

import com.zxplat.plugin.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 就诊 ↔ 平台 mdtrt_id 映射 {@code yb_encounter_platform_map} 仓储 */
public interface YbEncounterPlatformMapRepository extends JpaRepository<YbEncounterPlatformMap, Long> {

    /**
     * 按就诊与契约查找平台映射（不限 medType）。
     *
     * @param encounterId 就诊 ID
     * @param contractId  契约 ID
     * @param tenantId    租户 ID
     * @return 平台映射行
     */
    Optional<YbEncounterPlatformMap> findFirstByEncounterIdAndContractIdAndTenantIdAndIsDeletedFalse(
        Long encounterId, Long contractId, Long tenantId);

    /**
     * 按就诊、契约与医疗类别查找平台映射。
     *
     * @param encounterId 就诊 ID
     * @param contractId  契约 ID
     * @param medType     医疗类别
     * @param tenantId    租户 ID
     * @return 平台映射行
     */
    Optional<YbEncounterPlatformMap> findFirstByEncounterIdAndContractIdAndMedTypeAndTenantIdAndIsDeletedFalse(
        Long encounterId, Long contractId, String medType, Long tenantId);

    /**
     * 有效映射已软删时，取同一就诊、契约、医疗类别下最新一条仍带 mdtrt_id 的登记。
     *
     * @param encounterId 就诊 ID
     * @param contractId  契约 ID
     * @param medType     医疗类别
     * @param tenantId    租户 ID
     * @return 已软删的平台映射
     */
    Optional<YbEncounterPlatformMap> findFirstByEncounterIdAndContractIdAndMedTypeAndTenantIdAndIsDeletedTrueOrderByIdDesc(
        Long encounterId, Long contractId, String medType, Long tenantId);

    /**
     * 按就诊、契约与 mdtrt_id 查找平台映射。
     *
     * @param encounterId 就诊 ID
     * @param contractId  契约 ID
     * @param mdtrtId     平台就诊 ID
     * @param tenantId    租户 ID
     * @return 平台映射行
     */
    Optional<YbEncounterPlatformMap> findFirstByEncounterIdAndContractIdAndMdtrtIdAndTenantIdAndIsDeletedFalse(
        Long encounterId, Long contractId, String mdtrtId, Long tenantId);

    /**
     * 按就诊 ID 集合批量查询平台映射。
     *
     * @param encounterIds 就诊 ID 集合
     * @param tenantId     租户 ID
     * @return 平台映射列表
     */
    List<YbEncounterPlatformMap> findByEncounterIdInAndTenantIdAndIsDeletedFalse(
        Collection<Long> encounterIds, Long tenantId);
}
