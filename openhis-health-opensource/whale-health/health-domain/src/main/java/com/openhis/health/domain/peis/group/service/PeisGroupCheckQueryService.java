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
import com.openhis.health.domain.peis.group.entity.PeisGroupContractPackage;
import com.openhis.health.domain.peis.group.repository.PeisGroupContractPackageRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupContractRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupRosterRepository;
import com.openhis.health.domain.share.constants.PeisGroupRosterConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 团检单位/合同/批次/名册读侧（AS-PEIS-GRP-001）。
 */
@Service
@RequiredArgsConstructor
public class PeisGroupCheckQueryService {

    private final PeisGroupCompanyRepository companyRepository;
    private final PeisGroupContractRepository contractRepository;
    private final PeisGroupContractPackageRepository contractPackageRepository;
    private final PeisGroupBatchRepository batchRepository;
    private final PeisGroupRosterRepository rosterRepository;

    public List<PeisGroupCompany> listCompanies() {
        return companyRepository.findAll((root, q, cb) -> cb.isFalse(root.get("isDeleted")));
    }

    public PeisGroupCompany requireCompany(Long id) {
        return companyRepository.findById(id)
            .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
            .orElseThrow(() -> new BusinessException("COMPANY_REQUIRED", "请选择团检单位"));
    }

    public List<PeisGroupContract> listContracts(Long companyId) {
        return contractRepository.findByCompanyIdAndIsDeletedFalse(companyId);
    }

    public List<Long> listContractPackageIds(Long contractId) {
        List<Long> packageIds = contractPackageRepository
            .findByContractIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(contractId)
            .stream()
            .map(PeisGroupContractPackage::getPackageActivityId)
            .toList();
        if (!packageIds.isEmpty()) {
            return packageIds;
        }
        PeisGroupContract contract = contractRepository.findById(contractId)
            .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
            .orElse(null);
        if (contract != null && contract.getPackageActivityId() != null) {
            return List.of(contract.getPackageActivityId());
        }
        return List.of();
    }

    public Map<Long, List<Long>> mapContractPackageIds(List<Long> contractIds) {
        Map<Long, List<Long>> result = new HashMap<>();
        if (contractIds == null || contractIds.isEmpty()) {
            return result;
        }
        List<PeisGroupContractPackage> rows =
            contractPackageRepository.findByContractIdInAndIsDeletedFalseOrderBySortOrderAscIdAsc(contractIds);
        for (PeisGroupContractPackage row : rows) {
            result.computeIfAbsent(row.getContractId(), key -> new ArrayList<>())
                .add(row.getPackageActivityId());
        }
        for (Long contractId : contractIds) {
            if (result.containsKey(contractId)) {
                continue;
            }
            List<Long> fallback = listContractPackageIds(contractId);
            if (!fallback.isEmpty()) {
                result.put(contractId, fallback);
            }
        }
        return result;
    }

    public PeisGroupContract requireContract(Long id) {
        return contractRepository.findById(id)
            .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
            .orElseThrow(() -> new BusinessException("CONTRACT_NOT_FOUND", "合同不存在"));
    }

    public List<PeisGroupBatch> listBatches(Long companyId) {
        return batchRepository.findByCompanyIdAndIsDeletedFalse(companyId);
    }

    public PeisGroupBatch requireBatch(Long id) {
        return batchRepository.findByIdAndIsDeletedFalse(id)
            .orElseThrow(() -> new BusinessException("BATCH_NOT_FOUND", "批次不存在"));
    }

    public Page<PeisGroupRoster> pageRoster(
        Long batchId,
        String payStatusCode,
        String enrollmentFilter,
        String arrivalStatusCode,
        Long collegeId,
        String className,
        Pageable pageable) {
        Specification<PeisGroupRoster> spec = (root, q, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isFalse(root.get("isDeleted")));
            preds.add(cb.equal(root.get("batchId"), batchId));
            if (payStatusCode != null && !payStatusCode.isBlank()) {
                preds.add(cb.equal(root.get("payStatusCode"), payStatusCode));
            }
            if ("ENROLLED".equals(enrollmentFilter)) {
                preds.add(root.get("payStatusCode").in(
                    PeisGroupRosterConstants.PAY_PAID, PeisGroupRosterConstants.PAY_ENROLLED));
            } else if ("UNENROLLED".equals(enrollmentFilter)) {
                preds.add(cb.equal(root.get("payStatusCode"), PeisGroupRosterConstants.PAY_UNPAID));
            }
            if (arrivalStatusCode != null && !arrivalStatusCode.isBlank()) {
                preds.add(cb.equal(root.get("arrivalStatusCode"), arrivalStatusCode));
            }
            if (collegeId != null) {
                preds.add(cb.equal(root.get("collegeId"), collegeId));
            }
            if (className != null && !className.isBlank()) {
                preds.add(cb.equal(root.get("className"), className.trim()));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
        return rosterRepository.findAll(spec, pageable);
    }

    public List<PeisGroupRoster> listRosterByBatch(Long batchId) {
        return rosterRepository.findByBatchIdAndIsDeletedFalse(batchId);
    }

    public List<PeisGroupRoster> listRosterByPatientId(Long patientId) {
        if (patientId == null) {
            return List.of();
        }
        return rosterRepository.findByPatientIdAndIsDeletedFalseOrderByUpdatedAtDesc(patientId);
    }

    public PeisGroupRoster requireRoster(Long id) {
        return rosterRepository.findByIdAndIsDeletedFalse(id)
            .orElseThrow(() -> new BusinessException("ROSTER_NOT_FOUND", "该人员不在本批次名单"));
    }

    /**
     * 按就诊反查团检名册（分科 BR-GRP-PAY-01 门禁）。
     *
     * @param encounterId PE 就诊主键
     * @return 名册行，不存在时 empty
     */
    public java.util.Optional<PeisGroupRoster> findRosterByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return java.util.Optional.empty();
        }
        return rosterRepository.findByEncounterIdAndIsDeletedFalse(encounterId);
    }

    public List<PeisGroupRoster> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return rosterRepository.findAllById(ids).stream()
            .filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
            .toList();
    }
}
