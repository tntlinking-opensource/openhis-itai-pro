/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.healtharchive.service;

import com.zxplat.plugin.peis.domain.healtharchive.entity.PeisPatientHealthArchive;
import com.zxplat.plugin.peis.domain.healtharchive.repository.PeisPatientHealthArchiveRepository;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PeisHealthArchiveDomainService {

    private final PeisPatientHealthArchiveRepository repository;

    public Optional<PeisPatientHealthArchive> findByPatientId(Long patientId) {
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null || patientId == null) {
            return Optional.empty();
        }
        return repository.findByTenantIdAndPatientIdAndIsDeletedFalse(tenantId, patientId);
    }

    public PeisPatientHealthArchive upsert(PeisPatientHealthArchive incoming, Long operatorId) {
        Long tenantId = TenantUtils.getCurrentTenantId();
        PeisPatientHealthArchive entity = repository
            .findByTenantIdAndPatientIdAndIsDeletedFalse(tenantId, incoming.getPatientId())
            .orElseGet(PeisPatientHealthArchive::new);
        LocalDateTime now = LocalDateTime.now();
        if (entity.getId() == null) {
            entity.setTenantId(tenantId);
            entity.setPatientId(incoming.getPatientId());
            entity.setCreatedAt(now);
            entity.setCreatedBy(operatorId);
            entity.setIsDeleted(Boolean.FALSE);
        }
        entity.setOrgId(incoming.getOrgId());
        entity.setPastHistory(incoming.getPastHistory());
        entity.setFamilyHistory(incoming.getFamilyHistory());
        entity.setHabitSummary(incoming.getHabitSummary());
        entity.setAllergySummary(incoming.getAllergySummary());
        entity.setTagCode(incoming.getTagCode());
        entity.setRemark(incoming.getRemark());
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operatorId);
        return repository.save(entity);
    }
}
