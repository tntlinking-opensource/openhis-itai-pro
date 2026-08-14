/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.service;

import com.openhis.chs.domain.share.enums.MedType;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.openhis.chs.domain.outpatient.repository.YbEncounterPlatformMapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** 就诊 ↔ 平台 mdtrt_id 映射落库（2201/2401 后写入） */
@Service
@RequiredArgsConstructor
public class YbEncounterPlatformMapDomainService {

    private final YbEncounterPlatformMapRepository encounterPlatformMapRepository;

    /**
     * 平台挂号/入院登记成功后写入就诊 ↔ mdtrt_id 映射（幂等）。
     *
     * @param encounterId 就诊 ID
     * @param contractId  契约 ID
     * @param medType     医疗类别
     * @param mdtrtId     平台就诊 ID
     * @param psnNo       人员编号
     * @param iptOtpNo    住院/门诊号
     * @param tenantId    租户 ID
     * @return 持久化后的映射行
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
            // Pre：registerFresh 软删未命中时勿直接复用旧 mdtrt，覆盖为平台新就诊号
            if (!normalizedMdtrtId.equals(existing.getMdtrtId())) {
                existing.setMdtrtId(normalizedMdtrtId);
                if (isNotEmpty(psnNo)) {
                    existing.setPsnNo(psnNo);
                }
                if (isNotEmpty(iptOtpNo)) {
                    existing.setIptOtpNo(iptOtpNo);
                }
                return encounterPlatformMapRepository.saveAndFlush(existing);
            }
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

    /**
     * 平台撤销挂号/入院登记后软删除对应映射。
     *
     * @param encounterId 就诊 ID
     * @param contractId  契约 ID
     * @param medType     医疗类别
     * @param tenantId    租户 ID
     */
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

    /**
     * 查找就诊在指定契约与医疗类别下已登记的平台 mdtrt_id。
     *
     * @param encounterId 就诊 ID
     * @param contractId  契约 ID
     * @param medType     医疗类别
     * @param tenantId    租户 ID
     * @return 平台 mdtrt_id；未登记时返回空
     */
    public Optional<String> findExistingMdtrtId(
        Long encounterId,
        Long contractId,
        String medType,
        Long tenantId) {
        YbEncounterPlatformMap row = findActive(encounterId, contractId, medType.trim(), tenantId);
        if (row == null || !isNotEmpty(row.getMdtrtId())) {
            return Optional.empty();
        }
        return Optional.of(row.getMdtrtId());
    }

    /**
     * 按 mdtrt_id 或 medType 软删除就诊平台映射并记录操作人。
     *
     * @param encounterId 就诊 ID
     * @param contractId  契约 ID
     * @param mdtrtId     平台就诊 ID（优先匹配）
     * @param medType     医疗类别（次优匹配）
     * @param operatorId  操作人 ID
     * @param tenantId    租户 ID
     */
    public void softDeleteEncounterMap(
        Long encounterId,
        Long contractId,
        String mdtrtId,
        String medType,
        Long operatorId,
        Long tenantId) {
        Optional<YbEncounterPlatformMap> mapped = Optional.empty();
        if (isNotEmpty(mdtrtId)) {
            mapped = encounterPlatformMapRepository
                .findFirstByEncounterIdAndContractIdAndMdtrtIdAndTenantIdAndIsDeletedFalse(
                    encounterId, contractId, mdtrtId.trim(), tenantId);
        }
        if (mapped.isEmpty() && isNotEmpty(medType)) {
            mapped = encounterPlatformMapRepository
                .findFirstByEncounterIdAndContractIdAndMedTypeAndTenantIdAndIsDeletedFalse(
                    encounterId, contractId, medType.trim(), tenantId);
        }
        mapped.ifPresent(row -> {
            row.setIsDeleted(true);
            row.setDeletedAt(java.time.LocalDateTime.now());
            if (operatorId != null) {
                row.setDeletedBy(operatorId);
            }
            // Pre：立即刷库，避免同事务内 saveAfter 仍读到未删旧行并复用死 mdtrt
            encounterPlatformMapRepository.saveAndFlush(row);
        });
    }

    /**
     * 查找有效的就诊平台映射（mdtrt_id 非空）。
     *
     * @param encounterId 就诊 ID
     * @param contractId  契约 ID
     * @param medType     医疗类别
     * @param tenantId    租户 ID
     * @return 映射实体；不存在或 mdtrt_id 为空时返回 {@code null}
     */
    private YbEncounterPlatformMap findActive(
        Long encounterId,
        Long contractId,
        String medType,
        Long tenantId) {
        return encounterPlatformMapRepository
            .findFirstByEncounterIdAndContractIdAndMedTypeAndTenantIdAndIsDeletedFalse(
                encounterId, contractId, medType, tenantId)
            .filter(row -> isNotEmpty(row.getMdtrtId()))
            .orElse(null);
    }
}
