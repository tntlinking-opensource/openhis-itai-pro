/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.settlement;

import com.openhis.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.openhis.chs.domain.outpatient.repository.YbEncounterPlatformMapRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** 就诊 ↔ 平台 mdtrt_id 映射落库（2201/2401 后写入）。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class YbEncounterPlatformMapWriter {

    private final YbEncounterPlatformMapRepository encounterPlatformMapRepository;

    /**
     * 2201/2401 成功后写入映射；并发或重复调用时幂等复用已有行。
     *
     * <p>勿在 save 前手动 {@code setId}：Hibernate 6 在已赋值主键上 {@code persist} 会报
     * detached entity passed to persist；主键由 {@code @SnowflakeId} 在 insert 时生成。</p>
     */
    public YbEncounterPlatformMap saveAfterPlatformRegistration(
        Long encounterId,
        Long contractId,
        String medType,
        String mdtrtId,
        String psnNo,
        String iptOtpNo,
        Long tenantId) {
        String normalizedMedType = medType.trim();
        String normalizedMdtrtId = mdtrtId.trim();
        YbEncounterPlatformMap existing = findActive(
            encounterId, contractId, normalizedMedType, tenantId);
        if (existing != null) {
            return existing;
        }
        YbEncounterPlatformMap row = new YbEncounterPlatformMap();
        row.setEncounterId(encounterId);
        row.setContractId(contractId);
        row.setMedType(normalizedMedType);
        row.setMdtrtId(normalizedMdtrtId);
        row.setPsnNo(psnNo);
        row.setIptOtpNo(iptOtpNo);
        row.setTenantId(tenantId);
        try {
            return encounterPlatformMapRepository.saveAndFlush(row);
        } catch (DataIntegrityViolationException ex) {
            YbEncounterPlatformMap raced = findActive(
                encounterId, contractId, normalizedMedType, tenantId);
            if (raced != null) {
                return raced;
            }
            throw ex;
        }
    }

    /** 2202 成功后软删映射。 */
    public void softDeleteAfterPlatformRevocation(
        Long encounterId,
        Long contractId,
        String medType,
        Long tenantId) {
        YbEncounterPlatformMap existing = findActive(encounterId, contractId, medType, tenantId);
        if (existing == null) {
            return;
        }
        existing.setIsDeleted(true);
        encounterPlatformMapRepository.save(existing);
    }

    private YbEncounterPlatformMap findActive(
        Long encounterId,
        Long contractId,
        String medType,
        Long tenantId) {
        return encounterPlatformMapRepository
            .findFirstByEncounterIdAndContractIdAndMedTypeAndTenantIdAndIsDeletedFalse(
                encounterId, contractId, medType, tenantId)
            .filter(row -> StringUtils.isNotBlank(row.getMdtrtId()))
            .orElse(null);
    }
}
