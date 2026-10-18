/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.outpatient.service;

import com.zxplat.plugin.chs.domain.share.enums.MedType;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.zxplat.plugin.chs.domain.outpatient.repository.YbEncounterPlatformMapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** 就诊 ↔ 平台 mdtrt_id 映射只读查询 */
@Service
@RequiredArgsConstructor
public class YbEncounterPlatformMapQueryService {

    private final YbEncounterPlatformMapRepository encounterPlatformMapRepository;

    /**
     * 按就诊、契约与医疗类别查找有效平台映射。
     *
     * @param encounterId 就诊 ID
     * @param contractId  契约 ID
     * @param medType     医疗类别
     * @param tenantId    租户 ID
     * @return 映射实体；参数无效或未找到时返回空
     */
    public Optional<YbEncounterPlatformMap> findOptionalByEncounterContractMedType(
            Long encounterId,
            Long contractId,
            String medType,
            Long tenantId) {
        if (encounterId == null || contractId == null || tenantId == null || !isNotEmpty(medType)) {
            return Optional.empty();
        }
        return encounterPlatformMapRepository
            .findFirstByEncounterIdAndContractIdAndMedTypeAndTenantIdAndIsDeletedFalse(
                encounterId, contractId, medType.trim(), tenantId)
            .filter(row -> isNotEmpty(row.getMdtrtId()));
    }

    /**
     * 有效映射不在时，取最近一条已软删且仍有 mdtrt_id 的登记，供补撤 2202。
     *
     * @param encounterId 就诊 ID
     * @param contractId  契约 ID
     * @param medType     医疗类别
     * @param tenantId    租户 ID
     * @return 已软删映射；参数无效或没有 mdtrt_id 时返回空
     */
    public Optional<YbEncounterPlatformMap> findLatestDeletedByEncounterContractMedType(
            Long encounterId,
            Long contractId,
            String medType,
            Long tenantId) {
        if (encounterId == null || contractId == null || tenantId == null || !isNotEmpty(medType)) {
            return Optional.empty();
        }
        return encounterPlatformMapRepository
            .findFirstByEncounterIdAndContractIdAndMedTypeAndTenantIdAndIsDeletedTrueOrderByIdDesc(
                encounterId, contractId, medType.trim(), tenantId)
            .filter(row -> isNotEmpty(row.getMdtrtId()));
    }

    /**
     * 按就诊 ID 集合批量查询平台映射。
     *
     * @param encounterIds 就诊 ID 集合
     * @param tenantId     租户 ID
     * @return 映射列表；参数无效时返回空列表
     */
    public List<YbEncounterPlatformMap> listByEncounterIds(Collection<Long> encounterIds, Long tenantId) {
        if (encounterIds == null || encounterIds.isEmpty() || tenantId == null) {
            return Collections.emptyList();
        }
        return encounterPlatformMapRepository.findByEncounterIdInAndTenantIdAndIsDeletedFalse(encounterIds, tenantId);
    }
}
