/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.group.service;

import com.openhis.health.domain.peis.group.entity.PeisGroupBatch;
import com.openhis.health.domain.peis.group.entity.PeisGroupCompany;
import com.openhis.health.domain.peis.group.entity.PeisGroupContract;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.peis.group.repository.PeisGroupBatchRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupCompanyRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupContractRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupRosterRepository;
import com.openhis.health.domain.share.constants.PeisGroupRosterConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 团检单位/合同/批次/名册写侧。
 */
@Service
@RequiredArgsConstructor
public class PeisGroupCheckDomainService {

    private final PeisGroupCompanyRepository companyRepository;
    private final PeisGroupContractRepository contractRepository;
    private final PeisGroupBatchRepository batchRepository;
    private final PeisGroupRosterRepository rosterRepository;

    public PeisGroupCompany saveCompany(PeisGroupCompany entity, Long operatorId) {
        touch(entity, operatorId);
        return companyRepository.save(entity);
    }

    public PeisGroupContract saveContract(PeisGroupContract entity, Long operatorId) {
        if (entity.getCompanyId() == null || entity.getPackageActivityId() == null) {
            throw new BusinessException("GROUP_CONTRACT_INVALID", "单位与套餐不能为空");
        }
        touch(entity, operatorId);
        return contractRepository.save(entity);
    }

    public PeisGroupBatch saveBatch(PeisGroupBatch entity, Long operatorId) {
        if (entity.getCompanyId() == null || entity.getContractId() == null
            || entity.getPackageActivityId() == null) {
            throw new BusinessException("GROUP_BATCH_INVALID", "单位、合同与套餐不能为空");
        }
        if (entity.getStatusCode() == null) {
            entity.setStatusCode("DRAFT");
        }
        touch(entity, operatorId);
        return batchRepository.save(entity);
    }

    public PeisGroupRoster saveRoster(PeisGroupRoster entity, Long operatorId) {
        if (entity.getBatchId() == null || entity.getFullName() == null || entity.getFullName().isBlank()) {
            throw new BusinessException("GROUP_ROSTER_INVALID", "批次与姓名不能为空");
        }
        if (entity.getPayStatusCode() == null) {
            entity.setPayStatusCode(PeisGroupRosterConstants.PAY_UNPAID);
        }
        if (entity.getArrivalStatusCode() == null) {
            entity.setArrivalStatusCode(PeisGroupRosterConstants.ARRIVAL_PENDING);
        }
        touch(entity, operatorId);
        return rosterRepository.save(entity);
    }

    public int importRosterRows(Long batchId, List<PeisGroupRoster> rows, Long operatorId) {
        int imported = 0;
        for (PeisGroupRoster row : rows) {
            if (row.getPatientId() != null
                && rosterRepository.existsByBatchIdAndPatientIdAndIsDeletedFalse(batchId, row.getPatientId())) {
                continue;
            }
            row.setId(null);
            row.setBatchId(batchId);
            saveRoster(row, operatorId);
            imported++;
        }
        return imported;
    }

    public PeisGroupRoster markPaid(Long rosterId, Long encounterId, Long operatorId) {
        PeisGroupRoster roster = requireRoster(rosterId);
        roster.setPayStatusCode(PeisGroupRosterConstants.PAY_PAID);
        roster.setEncounterId(encounterId);
        touch(roster, operatorId);
        return rosterRepository.save(roster);
    }

    public PeisGroupRoster confirmArrival(Long rosterId, Long operatorId) {
        PeisGroupRoster roster = requireRoster(rosterId);
        if (!PeisGroupRosterConstants.PAY_PAID.equals(roster.getPayStatusCode())
            || roster.getEncounterId() == null) {
            throw new BusinessException("NOT_PAID", "请先完成团检收费");
        }
        roster.setArrivalStatusCode(PeisGroupRosterConstants.ARRIVAL_ARRIVED);
        touch(roster, operatorId);
        return rosterRepository.save(roster);
    }

    private PeisGroupRoster requireRoster(Long rosterId) {
        return rosterRepository.findByIdAndIsDeletedFalse(rosterId)
            .orElseThrow(() -> new BusinessException("ROSTER_NOT_FOUND", "该人员不在本批次名单"));
    }

    private void touch(com.openhis.whale.core.domain.BaseAuditedEntity entity, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        if (entity.getTenantId() == null) {
            entity.setTenantId(TenantUtils.getCurrentTenantId());
        }
        if (entity.getId() == null) {
            entity.setCreatedAt(now);
            entity.setCreatedBy(operatorId);
            entity.setIsDeleted(Boolean.FALSE);
        }
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operatorId);
    }
}
