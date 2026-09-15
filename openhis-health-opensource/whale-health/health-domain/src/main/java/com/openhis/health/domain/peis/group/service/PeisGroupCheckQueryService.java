/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.group.service;

import com.openhis.health.domain.peis.group.entity.PeisContractType;
import com.openhis.health.domain.peis.group.entity.PeisGroupBatch;
import com.openhis.health.domain.peis.group.entity.PeisGroupBatchPackage;
import com.openhis.health.domain.peis.group.entity.PeisGroupCompany;
import com.openhis.health.domain.peis.group.entity.PeisGroupContract;
import com.openhis.health.domain.peis.group.entity.PeisGroupContractPackage;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.peis.group.entity.PeisGroupRosterPackage;
import com.openhis.health.domain.peis.group.repository.PeisContractTypeRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupBatchPackageRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupBatchRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupCompanyRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupContractPackageRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupContractRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupRosterPackageRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupRosterRepository;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.PeisGroupRosterConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 团检单位/合同/体检分组/名册读侧（AS-PEIS-GRP-001 · PEIS-GRP-TREE-PKG-001）。
 *
 * <p>仅本聚合 Repository；跨聚合 enrich 见 application.peis.group.support。</p>
 */
@Service
@RequiredArgsConstructor
public class PeisGroupCheckQueryService {

    private final PeisGroupCompanyRepository companyRepository;
    private final PeisGroupContractRepository contractRepository;
    private final PeisGroupContractPackageRepository contractPackageRepository;
    private final PeisGroupBatchRepository batchRepository;
    private final PeisGroupBatchPackageRepository batchPackageRepository;
    private final PeisGroupRosterRepository rosterRepository;
    private final PeisGroupRosterPackageRepository rosterPackageRepository;
    private final PeisContractTypeRepository contractTypeRepository;

    /**
     * 列出已启用的合同类型字典（号源分池只读）。
     *
     * @return 启用中的类型列表
     */
    public List<PeisContractType> listEnabledContractTypes() {
        return contractTypeRepository.findByEnabledFlagAndIsDeletedFalseOrderBySortOrderAscIdAsc(Boolean.TRUE);
    }

    /**
     * 按编码取类型显示名；未知编码返回空。
     *
     * @param typeCode 类型编码
     * @return 显示名或 empty
     */
    public Optional<String> findContractTypeName(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            return Optional.empty();
        }
        return contractTypeRepository.findFirstByTypeCodeAndIsDeletedFalse(typeCode.trim())
            .map(PeisContractType::getTypeName);
    }

    /**
     * 解析患者当日有效团检合同（名册→批次→合同；有效期内且未删除；不校验支付态）。
     *
     * @param patientId 患者主键
     * @param onDate    判定日期（通常为当天）
     * @return 去重后的有效合同列表
     */
    public List<PeisGroupContract> listValidContractsForPatient(Long patientId, LocalDate onDate) {
        if (patientId == null) {
            return List.of();
        }
        LocalDate day = onDate;
        if (day == null) {
            day = LocalDate.now();
        }
        List<PeisGroupRoster> rosters =
            rosterRepository.findByPatientIdAndIsDeletedFalseOrderByUpdatedAtDesc(patientId);
        if (rosters.isEmpty()) {
            return List.of();
        }
        Set<Long> batchIds = new HashSet<>();
        for (PeisGroupRoster roster : rosters) {
            if (roster.getBatchId() != null) {
                batchIds.add(roster.getBatchId());
            }
        }
        if (batchIds.isEmpty()) {
            return List.of();
        }
        Map<Long, PeisGroupContract> byId = new LinkedHashMap<>();
        for (Long batchId : batchIds) {
            Optional<PeisGroupBatch> batchOpt = batchRepository.findById(batchId)
                .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()));
            if (batchOpt.isEmpty() || batchOpt.get().getContractId() == null) {
                continue;
            }
            Long contractId = batchOpt.get().getContractId();
            if (byId.containsKey(contractId)) {
                continue;
            }
            Optional<PeisGroupContract> contractOpt = contractRepository.findById(contractId)
                .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()));
            if (contractOpt.isEmpty()) {
                continue;
            }
            PeisGroupContract contract = contractOpt.get();
            if (!isContractValidOn(contract, day)) {
                continue;
            }
            byId.put(contractId, contract);
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * 判断合同在指定日是否有效（边界空则不限制该侧）。
     *
     * @param contract 合同
     * @param day      判定日
     * @return true 有效
     */
    public boolean isContractValidOn(PeisGroupContract contract, LocalDate day) {
        if (contract == null || day == null) {
            return false;
        }
        if (Boolean.TRUE.equals(contract.getIsDeleted())) {
            return false;
        }
        if (contract.getValidFrom() != null && day.isBefore(contract.getValidFrom())) {
            return false;
        }
        if (contract.getValidTo() != null && day.isAfter(contract.getValidTo())) {
            return false;
        }
        return true;
    }

    public List<PeisGroupCompany> listCompanies() {
        return companyRepository.findAll((root, q, cb) -> cb.isFalse(root.get("isDeleted")));
    }

    public List<PeisGroupCompany> listRootCompanies() {
        return companyRepository.findByParentIdIsNullAndIsDeletedFalse();
    }

    public List<PeisGroupCompany> listChildCompanies(Long parentId) {
        if (parentId == null) {
            return List.of();
        }
        return companyRepository.findByParentIdAndIsDeletedFalse(parentId);
    }

    /**
     * 解析单位节点及其全部子孙 id（含自身）。
     *
     * @param rootCompanyId 树根或选中节点
     * @return 节点 id 列表
     */
    public List<Long> listDescendantCompanyIds(Long rootCompanyId) {
        if (rootCompanyId == null) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootCompanyId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (current == null || !visited.add(current)) {
                continue;
            }
            result.add(current);
            for (PeisGroupCompany child : companyRepository.findByParentIdAndIsDeletedFalse(current)) {
                queue.add(child.getId());
            }
        }
        return result;
    }

    public PeisGroupCompany requireCompany(Long id) {
        return companyRepository.findById(id)
            .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
            .orElseThrow(() -> new BusinessException("COMPANY_REQUIRED", "请选择团检单位"));
    }

    public List<PeisGroupContract> listContracts(Long companyId) {
        return contractRepository.findByCompanyIdAndIsDeletedFalse(companyId);
    }

    /**
     * 列出覆盖指定节点（含下级）的全部合同。
     *
     * @param companyId 选中树节点
     * @return 合同列表
     */
    public List<PeisGroupContract> listContractsCoveringCompany(Long companyId) {
        List<Long> scopeIds = listDescendantCompanyIds(companyId);
        if (scopeIds.isEmpty()) {
            return List.of();
        }
        return listContractsByCompanyIds(scopeIds);
    }

    public List<PeisGroupContract> listContractsByCompanyIds(List<Long> companyIds) {
        if (companyIds == null || companyIds.isEmpty()) {
            return List.of();
        }
        List<PeisGroupContract> result = new ArrayList<>();
        int chunkSize = GeneralSharedConstants.ID_IN_QUERY_CHUNK_SIZE;
        for (int index = 0; index < companyIds.size(); index += chunkSize) {
            int end = Math.min(index + chunkSize, companyIds.size());
            List<Long> chunk = companyIds.subList(index, end);
            result.addAll(contractRepository.findByCompanyIdInAndIsDeletedFalse(chunk));
        }
        return result;
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

    public List<PeisGroupBatch> listBatchesCoveringCompany(Long companyId) {
        List<Long> scopeIds = listDescendantCompanyIds(companyId);
        if (scopeIds.isEmpty()) {
            return List.of();
        }
        return listBatchesByCompanyIds(scopeIds);
    }

    public List<PeisGroupBatch> listBatchesByCompanyIds(List<Long> companyIds) {
        if (companyIds == null || companyIds.isEmpty()) {
            return List.of();
        }
        List<PeisGroupBatch> result = new ArrayList<>();
        int chunkSize = GeneralSharedConstants.ID_IN_QUERY_CHUNK_SIZE;
        for (int index = 0; index < companyIds.size(); index += chunkSize) {
            int end = Math.min(index + chunkSize, companyIds.size());
            List<Long> chunk = companyIds.subList(index, end);
            result.addAll(batchRepository.findByCompanyIdInAndIsDeletedFalse(chunk));
        }
        return result;
    }

    public List<PeisGroupBatch> listBatchesByContractId(Long contractId) {
        if (contractId == null) {
            return List.of();
        }
        return batchRepository.findByContractIdAndIsDeletedFalse(contractId);
    }

    public PeisGroupBatch requireBatch(Long id) {
        return batchRepository.findByIdAndIsDeletedFalse(id)
            .orElseThrow(() -> new BusinessException("BATCH_NOT_FOUND", "批次不存在"));
    }

    public PeisGroupBatch requireBatchByNameUnderContract(Long contractId, String batchName) {
        if (contractId == null || batchName == null || batchName.isBlank()) {
            throw new BusinessException("BATCH_NOT_FOUND", "分组名称无效");
        }
        return batchRepository.findByContractIdAndBatchNameAndIsDeletedFalse(contractId, batchName.trim())
            .orElseThrow(() -> new BusinessException("BATCH_NOT_FOUND", "合同下不存在分组「" + batchName.trim() + "」"));
    }

    public List<Long> listBatchPackageIds(Long batchId) {
        List<Long> packageIds = batchPackageRepository
            .findByBatchIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(batchId)
            .stream()
            .map(PeisGroupBatchPackage::getPackageActivityId)
            .toList();
        if (!packageIds.isEmpty()) {
            return packageIds;
        }
        PeisGroupBatch batch = batchRepository.findByIdAndIsDeletedFalse(batchId).orElse(null);
        if (batch != null && batch.getPackageActivityId() != null) {
            return List.of(batch.getPackageActivityId());
        }
        return List.of();
    }

    public Map<Long, List<Long>> mapBatchPackageIds(List<Long> batchIds) {
        Map<Long, List<Long>> result = new HashMap<>();
        if (batchIds == null || batchIds.isEmpty()) {
            return result;
        }
        List<PeisGroupBatchPackage> rows =
            batchPackageRepository.findByBatchIdInAndIsDeletedFalseOrderBySortOrderAscIdAsc(batchIds);
        for (PeisGroupBatchPackage row : rows) {
            result.computeIfAbsent(row.getBatchId(), key -> new ArrayList<>())
                .add(row.getPackageActivityId());
        }
        for (Long batchId : batchIds) {
            if (result.containsKey(batchId)) {
                continue;
            }
            List<Long> fallback = listBatchPackageIds(batchId);
            if (!fallback.isEmpty()) {
                result.put(batchId, fallback);
            }
        }
        return result;
    }

    public List<Long> listRosterOptionalPackageIds(Long rosterId) {
        return rosterPackageRepository.findByRosterIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(rosterId)
            .stream()
            .map(PeisGroupRosterPackage::getPackageActivityId)
            .toList();
    }

    public Map<Long, List<Long>> mapRosterOptionalPackageIds(List<Long> rosterIds) {
        Map<Long, List<Long>> result = new HashMap<>();
        if (rosterIds == null || rosterIds.isEmpty()) {
            return result;
        }
        List<PeisGroupRosterPackage> rows =
            rosterPackageRepository.findByRosterIdInAndIsDeletedFalseOrderBySortOrderAscIdAsc(rosterIds);
        for (PeisGroupRosterPackage row : rows) {
            result.computeIfAbsent(row.getRosterId(), key -> new ArrayList<>())
                .add(row.getPackageActivityId());
        }
        return result;
    }

    /**
     * 按患者查找唯一在册团检名册（BR-GRP-ROS-02）。
     *
     * @param patientId 患者主键
     * @return 唯一名册；不存在或多条时 empty
     */
    public Optional<PeisGroupRoster> findUniqueActiveRosterByPatientId(Long patientId) {
        if (patientId == null) {
            return Optional.empty();
        }
        List<PeisGroupRoster> rows = rosterRepository.findByPatientIdAndIsDeletedFalseOrderByUpdatedAtDesc(patientId);
        if (rows.size() == 1) {
            return Optional.of(rows.get(0));
        }
        return Optional.empty();
    }

    public Page<PeisGroupRoster> pageRoster(
        Long batchId,
        String payStatusCode,
        String enrollmentFilter,
        String arrivalStatusCode,
        Long collegeId,
        String className,
        Pageable pageable) {
        Specification<PeisGroupRoster> spec = buildRosterSpec(
            batchId, null, payStatusCode, enrollmentFilter, arrivalStatusCode, collegeId, className);
        return rosterRepository.findAll(spec, pageable);
    }

    /**
     * 按合同（可选分组）分页查询名册。
     *
     * @param contractId  合同主键
     * @param batchIdFilter 分组过滤（可选）
     * @param pageable    分页
     * @return 名册分页
     */
    public Page<PeisGroupRoster> pageRosterByContract(
            Long contractId,
            Long batchIdFilter,
            String payStatusCode,
            String enrollmentFilter,
            String arrivalStatusCode,
            Long collegeId,
            String className,
            Pageable pageable) {
        List<Long> batchIds;
        if (batchIdFilter != null) {
            batchIds = List.of(batchIdFilter);
        } else {
            batchIds = listBatchesByContractId(contractId).stream().map(PeisGroupBatch::getId).toList();
        }
        if (batchIds.isEmpty()) {
            return Page.empty(pageable);
        }
        Specification<PeisGroupRoster> spec = buildRosterSpec(
            null, batchIds, payStatusCode, enrollmentFilter, arrivalStatusCode, collegeId, className);
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
    public Optional<PeisGroupRoster> findRosterByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return Optional.empty();
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

    private Specification<PeisGroupRoster> buildRosterSpec(
            Long batchId,
            List<Long> batchIds,
            String payStatusCode,
            String enrollmentFilter,
            String arrivalStatusCode,
            Long collegeId,
            String className) {
        return (root, q, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isFalse(root.get("isDeleted")));
            if (batchId != null) {
                preds.add(cb.equal(root.get("batchId"), batchId));
            }
            if (batchIds != null && !batchIds.isEmpty()) {
                preds.add(root.get("batchId").in(batchIds));
            }
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
    }
}
