/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.group.service;

import com.openhis.health.domain.peis.group.entity.PeisGroupBatch;
import com.openhis.health.domain.peis.group.entity.PeisGroupBatchPackage;
import com.openhis.health.domain.peis.group.entity.PeisGroupCompany;
import com.openhis.health.domain.peis.group.entity.PeisGroupContract;
import com.openhis.health.domain.peis.group.entity.PeisGroupContractPackage;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.peis.group.entity.PeisGroupRosterPackage;
import com.openhis.health.domain.peis.group.repository.PeisGroupBatchPackageRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupBatchRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupCompanyRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupContractPackageRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupContractRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupRosterPackageRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupRosterRepository;
import com.openhis.health.domain.share.constants.PeisGroupContractConstants;
import com.openhis.health.domain.share.constants.PeisGroupRosterConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 团检单位/合同/体检分组/名册写侧（DM-PEIS-GRP · PEIS-GRP-TREE-PKG-001）。
 *
 * <p>更新路径须先 load 托管实体再 save，避免 {@code AuditedEntityBase} detached persist。</p>
 */
@Service
@RequiredArgsConstructor
public class PeisGroupCheckDomainService {

    private final PeisGroupCompanyRepository companyRepository;
    private final PeisGroupContractRepository contractRepository;
    private final PeisGroupContractPackageRepository contractPackageRepository;
    private final PeisGroupBatchRepository batchRepository;
    private final PeisGroupBatchPackageRepository batchPackageRepository;
    private final PeisGroupRosterRepository rosterRepository;
    private final PeisGroupRosterPackageRepository rosterPackageRepository;

    /**
     * 保存团检单位（新建或更新；支持 parentId 树结构）。
     *
     * @param patch      待写入字段；有 id 时走更新
     * @param operatorId 操作人
     * @return 持久化后的单位
     */
    public PeisGroupCompany saveCompany(PeisGroupCompany patch, Long operatorId) {
        if (patch.getId() != null && patch.getParentId() != null) {
            assertNoCompanyCycle(patch.getId(), patch.getParentId());
        }
        PeisGroupCompany target = resolveCompanyForSave(patch);
        touch(target, operatorId);
        return companyRepository.save(target);
    }

    /**
     * 软删团检单位（BR-GRP-TREE-01：有子节点或合同时拒绝）。
     *
     * @param companyId  单位主键
     * @param operatorId 操作人
     */
    public void deleteCompany(Long companyId, Long operatorId) {
        PeisGroupCompany company = companyRepository.findById(companyId)
            .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
            .orElseThrow(() -> new BusinessException("COMPANY_NOT_FOUND", "单位不存在"));
        if (companyRepository.existsByParentIdAndIsDeletedFalse(companyId)) {
            throw new BusinessException("COMPANY_HAS_CHILDREN", "存在下级单位，无法删除");
        }
        if (contractRepository.existsByCompanyIdAndIsDeletedFalse(companyId)) {
            throw new BusinessException("COMPANY_HAS_CONTRACT", "存在未删合同，无法删除");
        }
        LocalDateTime now = LocalDateTime.now();
        company.setIsDeleted(Boolean.TRUE);
        company.setDeletedAt(now);
        company.setDeletedBy(operatorId);
        company.setUpdatedAt(now);
        company.setUpdatedBy(operatorId);
        companyRepository.save(company);
    }

    /**
     * 保存团检合同并替换可选套餐关联行。
     *
     * @param patch              合同字段
     * @param packageActivityIds 合同可选套餐（至少一项）
     * @param operatorId         操作人
     * @return 持久化后的合同
     */
    public PeisGroupContract saveContract(PeisGroupContract patch, List<Long> packageActivityIds, Long operatorId) {
        if (patch.getCompanyId() == null || packageActivityIds == null || packageActivityIds.isEmpty()) {
            throw new BusinessException("GROUP_CONTRACT_INVALID", "单位与套餐不能为空");
        }
        List<Long> normalizedIds = normalizePackageIds(packageActivityIds);
        PeisGroupContract target = resolveContractForSave(patch);
        target.setPackageActivityId(normalizedIds.get(0));
        if (target.getPayStatusCode() == null) {
            target.setPayStatusCode(PeisGroupContractConstants.PAY_UNPAID);
        }
        touch(target, operatorId);
        PeisGroupContract saved = contractRepository.save(target);
        replaceContractPackages(saved.getId(), normalizedIds, operatorId);
        return saved;
    }

    /**
     * 校验套餐是否在合同可选范围内。
     *
     * @param contractId        合同主键
     * @param packageActivityId 套餐 AD 主键
     */
    public void assertPackageAllowedForContract(Long contractId, Long packageActivityId) {
        if (contractId == null || packageActivityId == null) {
            throw new BusinessException("GROUP_BATCH_INVALID", "合同与套餐不能为空");
        }
        List<Long> allowed = listContractPackageIds(contractId);
        if (allowed.isEmpty()) {
            PeisGroupContract contract = contractRepository.findById(contractId)
                .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
                .orElseThrow(() -> new BusinessException("CONTRACT_NOT_FOUND", "合同不存在"));
            if (contract.getPackageActivityId() != null
                && contract.getPackageActivityId().equals(packageActivityId)) {
                return;
            }
            throw new BusinessException("GROUP_CONTRACT_PACKAGE_INVALID", "合同未配置可选套餐");
        }
        if (!allowed.contains(packageActivityId)) {
            throw new BusinessException("GROUP_CONTRACT_PACKAGE_INVALID", "套餐不在合同可选范围内");
        }
    }

    /**
     * 校验套餐是否在该体检分组可选范围内。
     *
     * @param batchId           分组主键
     * @param packageActivityId 套餐 AD 主键
     */
    public void assertPackageAllowedForBatch(Long batchId, Long packageActivityId) {
        List<Long> allowed = listBatchPackageIds(batchId);
        if (allowed.isEmpty() || !allowed.contains(packageActivityId)) {
            throw new BusinessException("GROUP_BATCH_PACKAGE_INVALID", "套餐不在本分组可选范围内");
        }
    }

    public List<Long> listContractPackageIds(Long contractId) {
        return contractPackageRepository.findByContractIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(contractId)
            .stream()
            .map(PeisGroupContractPackage::getPackageActivityId)
            .toList();
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

    /**
     * 保存体检分组并替换本组可选套餐（BR-GRP-GRP-01）。
     *
     * @param patch              分组字段
     * @param packageActivityIds 本组可选套餐
     * @param operatorId         操作人
     * @return 持久化后的分组
     */
    public PeisGroupBatch saveBatch(PeisGroupBatch patch, List<Long> packageActivityIds, Long operatorId) {
        if (packageActivityIds == null || packageActivityIds.isEmpty()) {
            throw new BusinessException("GROUP_BATCH_INVALID", "体检分组须至少配置一个套餐");
        }
        List<Long> normalized = normalizePackageIds(packageActivityIds);
        for (Long packageActivityId : normalized) {
            assertPackageAllowedForContract(patch.getContractId(), packageActivityId);
        }
        PeisGroupBatch target = resolveBatchForSave(patch);
        target.setPackageActivityId(normalized.get(0));
        if (target.getCompanyId() == null || target.getContractId() == null) {
            throw new BusinessException("GROUP_BATCH_INVALID", "单位与合同不能为空");
        }
        touch(target, operatorId);
        PeisGroupBatch saved = batchRepository.save(target);
        replaceBatchPackages(saved.getId(), normalized, operatorId);
        return saved;
    }

    /**
     * 保存单条名册及可选套餐集合。
     *
     * @param entity               名册行
     * @param optionalPackageIds   可选套餐（非空）
     * @param operatorId           操作人
     * @return 持久化后的名册
     */
    public PeisGroupRoster saveRosterWithOptionalPackages(
            PeisGroupRoster entity, List<Long> optionalPackageIds, Long operatorId) {
        if (entity.getBatchId() == null || StringUtils.isEmpty(entity.getFullName())) {
            throw new BusinessException("GROUP_ROSTER_INVALID", "分组与姓名不能为空");
        }
        if (optionalPackageIds == null || optionalPackageIds.isEmpty()) {
            throw new BusinessException("GROUP_ROSTER_PACKAGE_EMPTY", "可选套餐不能为空");
        }
        List<Long> normalized = normalizePackageIds(optionalPackageIds);
        for (Long packageActivityId : normalized) {
            assertPackageAllowedForBatch(entity.getBatchId(), packageActivityId);
        }
        assertNoActiveRosterForPerson(entity, null);
        if (entity.getPayStatusCode() == null) {
            entity.setPayStatusCode(PeisGroupRosterConstants.PAY_UNPAID);
        }
        if (entity.getArrivalStatusCode() == null) {
            entity.setArrivalStatusCode(PeisGroupRosterConstants.ARRIVAL_PENDING);
        }
        touch(entity, operatorId);
        PeisGroupRoster saved = rosterRepository.save(entity);
        replaceRosterPackages(saved.getId(), normalized, operatorId);
        return saved;
    }

    /**
     * 保存单条名册（导入前由调用方设置 batchId；不含可选套餐维护）。
     *
     * @param entity     名册行
     * @param operatorId 操作人
     * @return 持久化后的名册
     */
    public PeisGroupRoster saveRoster(PeisGroupRoster entity, Long operatorId) {
        if (entity.getBatchId() == null || entity.getFullName() == null || entity.getFullName().isBlank()) {
            throw new BusinessException("GROUP_ROSTER_INVALID", "分组与姓名不能为空");
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

    /**
     * 批量导入名册（同患者同分组跳过；全院唯一校验）。
     *
     * @param batchId    分组主键
     * @param rows       待导入行（须已设置 optionalPackageIds）
     * @param operatorId 操作人
     * @return 实际导入的名册主键列表
     */
    public List<Long> importRosterRows(Long batchId, List<PeisGroupRosterImportRow> rows, Long operatorId) {
        List<Long> importedIds = new ArrayList<>();
        for (PeisGroupRosterImportRow importRow : rows) {
            PeisGroupRoster row = importRow.getRoster();
            if (row == null) {
                continue;
            }
            if (row.getPatientId() != null
                && rosterRepository.existsByBatchIdAndPatientIdAndIsDeletedFalse(batchId, row.getPatientId())) {
                continue;
            }
            row.setId(null);
            row.setBatchId(batchId);
            PeisGroupRoster saved = saveRosterWithOptionalPackages(
                row, importRow.getOptionalPackageIds(), operatorId);
            importedIds.add(saved.getId());
        }
        return importedIds;
    }

    /**
     * 写入或更新名册选定套餐（建单前或到检前换套餐）。
     *
     * @param rosterId            名册主键
     * @param packageActivityId   选定套餐 AD
     * @param operatorId          操作人
     * @return 更新后的名册
     */
    public PeisGroupRoster assignSelectedPackage(Long rosterId, Long packageActivityId, Long operatorId) {
        PeisGroupRoster roster = requireRoster(rosterId);
        List<Long> optionalIds = listRosterOptionalPackageIds(rosterId);
        if (!optionalIds.contains(packageActivityId)) {
            throw new BusinessException("PACKAGE_NOT_IN_OPTIONS", "选定套餐不在可选范围内");
        }
        roster.setSelectedPackageActivityId(packageActivityId);
        touch(roster, operatorId);
        return rosterRepository.save(roster);
    }

    /**
     * 替换名册可选套餐（BR-GRP-PKG-01）。
     *
     * @param rosterId             名册主键
     * @param optionalPackageIds   新的可选套餐
     * @param operatorId           操作人
     */
    public void replaceRosterOptionalPackages(Long rosterId, List<Long> optionalPackageIds, Long operatorId) {
        PeisGroupRoster roster = requireRoster(rosterId);
        if (PeisGroupRosterConstants.ARRIVAL_ARRIVED.equals(roster.getArrivalStatusCode())) {
            throw new BusinessException("ARRIVAL_ALREADY_CONFIRMED", "已到检，不可修改可选套餐");
        }
        List<Long> normalized = normalizePackageIds(optionalPackageIds);
        for (Long packageActivityId : normalized) {
            assertPackageAllowedForBatch(roster.getBatchId(), packageActivityId);
        }
        Long selected = roster.getSelectedPackageActivityId();
        if (selected != null && !normalized.contains(selected)) {
            roster.setSelectedPackageActivityId(null);
            touch(roster, operatorId);
            rosterRepository.save(roster);
        }
        replaceRosterPackages(rosterId, normalized, operatorId);
    }

    /**
     * 变更名册所属体检分组（BR-GRP-ROS-01；超出目标组则失败）。
     *
     * @param rosterId       名册主键
     * @param targetBatchId  目标分组
     * @param operatorId     操作人
     * @return 更新后的名册
     */
    public PeisGroupRoster changeRosterBatch(Long rosterId, Long targetBatchId, Long operatorId) {
        PeisGroupRoster roster = requireRoster(rosterId);
        if (PeisGroupRosterConstants.isEnrolled(roster.getPayStatusCode())
            && PeisGroupRosterConstants.ARRIVAL_ARRIVED.equals(roster.getArrivalStatusCode())) {
            throw new BusinessException("ARRIVAL_ALREADY_CONFIRMED", "已到检，不可换组");
        }
        PeisGroupBatch sourceBatch = batchRepository.findByIdAndIsDeletedFalse(roster.getBatchId())
            .orElseThrow(() -> new BusinessException("BATCH_NOT_FOUND", "原分组不存在"));
        PeisGroupBatch targetBatch = batchRepository.findByIdAndIsDeletedFalse(targetBatchId)
            .orElseThrow(() -> new BusinessException("BATCH_NOT_FOUND", "目标分组不存在"));
        if (!sourceBatch.getContractId().equals(targetBatch.getContractId())) {
            throw new BusinessException("ROSTER_BATCH_CONTRACT_MISMATCH", "目标分组须属同一合同");
        }
        List<Long> currentOptional = listRosterOptionalPackageIds(rosterId);
        List<Long> targetBatchPackages = listBatchPackageIds(targetBatchId);
        for (Long packageActivityId : currentOptional) {
            if (!targetBatchPackages.contains(packageActivityId)) {
                throw new BusinessException(
                    "ROSTER_PACKAGES_EXCEED_TARGET_BATCH",
                    "可选套餐超出目标分组范围，请先调整可选套餐后再换组");
            }
        }
        roster.setBatchId(targetBatchId);
        roster.setSelectedPackageActivityId(null);
        touch(roster, operatorId);
        return rosterRepository.save(roster);
    }

    public List<Long> listRosterOptionalPackageIds(Long rosterId) {
        return rosterPackageRepository.findByRosterIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(rosterId)
            .stream()
            .map(PeisGroupRosterPackage::getPackageActivityId)
            .toList();
    }

    public PeisGroupRoster markPaid(Long rosterId, Long encounterId, Long operatorId) {
        PeisGroupRoster roster = requireRoster(rosterId);
        roster.setPayStatusCode(PeisGroupRosterConstants.PAY_PAID);
        roster.setEncounterId(encounterId);
        touch(roster, operatorId);
        return rosterRepository.save(roster);
    }

    /**
     * 团检合同收款后标记 PAID。
     *
     * @param contractId 合同主键
     * @param operatorId 操作人
     * @return 更新后的合同
     */
    public PeisGroupContract markContractPaid(Long contractId, Long operatorId) {
        PeisGroupContract contract = contractRepository.findById(contractId)
            .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
            .orElseThrow(() -> new BusinessException("CONTRACT_NOT_FOUND", "合同不存在"));
        contract.setPayStatusCode(PeisGroupContractConstants.PAY_PAID);
        touch(contract, operatorId);
        return contractRepository.save(contract);
    }

    /**
     * P0-1 免收费建单：写入 Encounter 并标记 ENROLLED（不经过 Payment）。
     *
     * @param rosterId     名册主键
     * @param encounterId  已建 PE 就诊
     * @param operatorId   操作人
     * @return 更新后的名册
     */
    public PeisGroupRoster markEnrolled(Long rosterId, Long encounterId, Long operatorId) {
        PeisGroupRoster roster = requireRoster(rosterId);
        if (PeisGroupRosterConstants.isEnrolled(roster.getPayStatusCode())) {
            throw new BusinessException("ROSTER_ALREADY_ENROLLED", "该人员已建单");
        }
        if (roster.getSelectedPackageActivityId() == null) {
            throw new BusinessException("PACKAGE_NOT_SELECTED", "请先选定套餐");
        }
        roster.setPayStatusCode(PeisGroupRosterConstants.PAY_ENROLLED);
        roster.setEncounterId(encounterId);
        touch(roster, operatorId);
        return rosterRepository.save(roster);
    }

    /**
     * GRP-05 到检确认（须已建单且有关联 Encounter）。
     *
     * @param rosterId     名册主键
     * @param operatorId   操作人
     * @return 更新后的名册
     */
    public PeisGroupRoster confirmArrival(Long rosterId, Long operatorId) {
        PeisGroupRoster roster = requireRoster(rosterId);
        if (!PeisGroupRosterConstants.isEnrolled(roster.getPayStatusCode())
            || roster.getEncounterId() == null) {
            throw new BusinessException("NOT_ENROLLED", "请先完成团检建单");
        }
        roster.setArrivalStatusCode(PeisGroupRosterConstants.ARRIVAL_ARRIVED);
        touch(roster, operatorId);
        return rosterRepository.save(roster);
    }

    /**
     * 撤销到检确认：{@code ARRIVED} → {@code PENDING}。
     *
     * @param rosterId     名册主键
     * @param operatorId   操作人
     * @return 更新后的名册
     */
    public PeisGroupRoster cancelArrival(Long rosterId, Long operatorId) {
        PeisGroupRoster roster = requireRoster(rosterId);
        if (!PeisGroupRosterConstants.isEnrolled(roster.getPayStatusCode())) {
            throw new BusinessException("NOT_ENROLLED", "请先完成团检建单");
        }
        if (!PeisGroupRosterConstants.ARRIVAL_ARRIVED.equals(roster.getArrivalStatusCode())) {
            throw new BusinessException("ARRIVAL_NOT_CONFIRMED", "当前未到检，无需撤销");
        }
        roster.setArrivalStatusCode(PeisGroupRosterConstants.ARRIVAL_PENDING);
        touch(roster, operatorId);
        return rosterRepository.save(roster);
    }

    /**
     * 撤销免收费建单：名册回退 {@code UNPAID} 并清空 encounter 关联（就诊作废由应用层先行处理）。
     *
     * @param rosterId     名册主键
     * @param operatorId   操作人
     * @return 更新后的名册
     */
    public PeisGroupRoster revertEnrollment(Long rosterId, Long operatorId) {
        PeisGroupRoster roster = requireRoster(rosterId);
        if (!PeisGroupRosterConstants.PAY_ENROLLED.equals(roster.getPayStatusCode())) {
            throw new BusinessException("ENROLLMENT_NOT_REVERSIBLE", "仅免收费建单可撤销；已收费请走退费流程");
        }
        if (PeisGroupRosterConstants.ARRIVAL_ARRIVED.equals(roster.getArrivalStatusCode())) {
            throw new BusinessException("ARRIVAL_ALREADY_CONFIRMED", "请先撤销到检后再撤销建单");
        }
        roster.setPayStatusCode(PeisGroupRosterConstants.PAY_UNPAID);
        roster.setEncounterId(null);
        roster.setArrivalStatusCode(PeisGroupRosterConstants.ARRIVAL_PENDING);
        touch(roster, operatorId);
        return rosterRepository.save(roster);
    }

    private void assertNoActiveRosterForPerson(PeisGroupRoster candidate, Long excludeRosterId) {
        Long excludeId = excludeRosterId;
        if (candidate.getId() != null) {
            excludeId = candidate.getId();
        }
        if (candidate.getPatientId() != null) {
            if (excludeId == null) {
                if (rosterRepository.existsByPatientIdAndIsDeletedFalse(candidate.getPatientId())) {
                    throw new BusinessException("ROSTER_ALREADY_ACTIVE", "该人员已有在册团检名册");
                }
            } else if (rosterRepository.existsByPatientIdAndIsDeletedFalseAndIdNot(
                candidate.getPatientId(), excludeId)) {
                throw new BusinessException("ROSTER_ALREADY_ACTIVE", "该人员已有在册团检名册");
            }
        }
        String idCard = trimToNull(candidate.getIdCardNo());
        if (idCard != null) {
            if (excludeId == null) {
                if (rosterRepository.existsByIdCardNoAndIsDeletedFalse(idCard)) {
                    throw new BusinessException("ROSTER_ALREADY_ACTIVE", "该身份证已有在册团检名册");
                }
            } else if (rosterRepository.existsByIdCardNoAndIsDeletedFalseAndIdNot(idCard, excludeId)) {
                throw new BusinessException("ROSTER_ALREADY_ACTIVE", "该身份证已有在册团检名册");
            }
        }
        String studentNo = trimToNull(candidate.getStudentNo());
        if (studentNo != null) {
            if (excludeId == null) {
                if (rosterRepository.existsByStudentNoAndIsDeletedFalse(studentNo)) {
                    throw new BusinessException("ROSTER_ALREADY_ACTIVE", "该学号已有在册团检名册");
                }
            } else if (rosterRepository.existsByStudentNoAndIsDeletedFalseAndIdNot(studentNo, excludeId)) {
                throw new BusinessException("ROSTER_ALREADY_ACTIVE", "该学号已有在册团检名册");
            }
        }
        String employeeNo = trimToNull(candidate.getEmployeeNo());
        if (employeeNo != null) {
            if (excludeId == null) {
                if (rosterRepository.existsByEmployeeNoAndIsDeletedFalse(employeeNo)) {
                    throw new BusinessException("ROSTER_ALREADY_ACTIVE", "该工号已有在册团检名册");
                }
            } else if (rosterRepository.existsByEmployeeNoAndIsDeletedFalseAndIdNot(employeeNo, excludeId)) {
                throw new BusinessException("ROSTER_ALREADY_ACTIVE", "该工号已有在册团检名册");
            }
        }
    }

    private void assertNoCompanyCycle(Long companyId, Long parentId) {
        Long cursor = parentId;
        while (cursor != null) {
            if (cursor.equals(companyId)) {
                throw new BusinessException("COMPANY_CYCLE", "上级单位不能形成环");
            }
            PeisGroupCompany parent = companyRepository.findById(cursor)
                .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
                .orElse(null);
            if (parent == null) {
                break;
            }
            cursor = parent.getParentId();
        }
    }

    private PeisGroupRoster requireRoster(Long rosterId) {
        return rosterRepository.findByIdAndIsDeletedFalse(rosterId)
            .orElseThrow(() -> new BusinessException("ROSTER_NOT_FOUND", "该人员不在本批次名单"));
    }

    private PeisGroupCompany resolveCompanyForSave(PeisGroupCompany patch) {
        if (patch.getId() == null) {
            return patch;
        }
        PeisGroupCompany existing = companyRepository.findById(patch.getId())
            .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
            .orElseThrow(() -> new BusinessException("COMPANY_NOT_FOUND", "单位不存在"));
        existing.setName(patch.getName());
        existing.setOrgId(patch.getOrgId());
        existing.setParentId(patch.getParentId());
        existing.setCampusCollegeId(patch.getCampusCollegeId());
        existing.setContactName(patch.getContactName());
        existing.setContactPhone(patch.getContactPhone());
        return existing;
    }

    private PeisGroupContract resolveContractForSave(PeisGroupContract patch) {
        if (patch.getId() == null) {
            return patch;
        }
        PeisGroupContract existing = contractRepository.findById(patch.getId())
            .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
            .orElseThrow(() -> new BusinessException("CONTRACT_NOT_FOUND", "合同不存在"));
        existing.setCompanyId(patch.getCompanyId());
        existing.setContractName(patch.getContractName());
        existing.setValidFrom(patch.getValidFrom());
        existing.setValidTo(patch.getValidTo());
        existing.setContractAmount(patch.getContractAmount());
        existing.setOrgId(patch.getOrgId());
        existing.setContractTypeCode(patch.getContractTypeCode());
        return existing;
    }

    private PeisGroupBatch resolveBatchForSave(PeisGroupBatch patch) {
        if (patch.getId() == null) {
            if (patch.getStatusCode() == null) {
                patch.setStatusCode("DRAFT");
            }
            return patch;
        }
        PeisGroupBatch existing = batchRepository.findByIdAndIsDeletedFalse(patch.getId())
            .orElseThrow(() -> new BusinessException("BATCH_NOT_FOUND", "批次不存在"));
        existing.setCompanyId(patch.getCompanyId());
        existing.setContractId(patch.getContractId());
        existing.setBatchName(patch.getBatchName());
        existing.setPackageActivityId(patch.getPackageActivityId());
        existing.setBookingDate(patch.getBookingDate());
        existing.setOrgId(patch.getOrgId());
        return existing;
    }

    private void replaceContractPackages(Long contractId, List<Long> packageActivityIds, Long operatorId) {
        softDeleteContractPackages(contractId, operatorId);
        saveContractPackageRows(contractId, packageActivityIds, operatorId);
    }

    private void replaceBatchPackages(Long batchId, List<Long> packageActivityIds, Long operatorId) {
        List<PeisGroupBatchPackage> existing =
            batchPackageRepository.findByBatchIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(batchId);
        LocalDateTime now = LocalDateTime.now();
        for (PeisGroupBatchPackage row : existing) {
            row.setIsDeleted(Boolean.TRUE);
            row.setDeletedAt(now);
            row.setDeletedBy(operatorId);
            row.setUpdatedAt(now);
            row.setUpdatedBy(operatorId);
        }
        batchPackageRepository.saveAll(existing);

        List<PeisGroupBatchPackage> rows = new ArrayList<>();
        int sort = 0;
        for (Long packageActivityId : packageActivityIds) {
            PeisGroupBatchPackage row = new PeisGroupBatchPackage();
            row.setBatchId(batchId);
            row.setPackageActivityId(packageActivityId);
            row.setSortOrder(sort++);
            touch(row, operatorId);
            rows.add(row);
        }
        batchPackageRepository.saveAll(rows);
    }

    private void replaceRosterPackages(Long rosterId, List<Long> packageActivityIds, Long operatorId) {
        List<PeisGroupRosterPackage> existing =
            rosterPackageRepository.findByRosterIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(rosterId);
        LocalDateTime now = LocalDateTime.now();
        for (PeisGroupRosterPackage row : existing) {
            row.setIsDeleted(Boolean.TRUE);
            row.setDeletedAt(now);
            row.setDeletedBy(operatorId);
            row.setUpdatedAt(now);
            row.setUpdatedBy(operatorId);
        }
        rosterPackageRepository.saveAll(existing);

        List<PeisGroupRosterPackage> rows = new ArrayList<>();
        int sort = 0;
        for (Long packageActivityId : packageActivityIds) {
            PeisGroupRosterPackage row = new PeisGroupRosterPackage();
            row.setRosterId(rosterId);
            row.setPackageActivityId(packageActivityId);
            row.setSortOrder(sort++);
            touch(row, operatorId);
            rows.add(row);
        }
        rosterPackageRepository.saveAll(rows);
    }

    private void softDeleteContractPackages(Long contractId, Long operatorId) {
        List<PeisGroupContractPackage> existing =
            contractPackageRepository.findByContractIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(contractId);
        LocalDateTime now = LocalDateTime.now();
        for (PeisGroupContractPackage row : existing) {
            row.setIsDeleted(Boolean.TRUE);
            row.setDeletedAt(now);
            row.setDeletedBy(operatorId);
            row.setUpdatedAt(now);
            row.setUpdatedBy(operatorId);
        }
        contractPackageRepository.saveAll(existing);
    }

    private void saveContractPackageRows(Long contractId, List<Long> packageActivityIds, Long operatorId) {
        List<PeisGroupContractPackage> rows = new ArrayList<>();
        int sort = 0;
        for (Long packageActivityId : packageActivityIds) {
            PeisGroupContractPackage row = new PeisGroupContractPackage();
            row.setContractId(contractId);
            row.setPackageActivityId(packageActivityId);
            row.setSortOrder(sort++);
            touch(row, operatorId);
            rows.add(row);
        }
        contractPackageRepository.saveAll(rows);
    }

    private static List<Long> normalizePackageIds(List<Long> packageActivityIds) {
        Set<Long> unique = new LinkedHashSet<>();
        for (Long packageActivityId : packageActivityIds) {
            if (packageActivityId != null) {
                unique.add(packageActivityId);
            }
        }
        if (unique.isEmpty()) {
            throw new BusinessException("GROUP_CONTRACT_INVALID", "套餐不能为空");
        }
        return List.copyOf(unique);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
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

    /**
     * 名册导入行（领域层中性载体，含可选套餐）。
     */
    @lombok.Getter
    @lombok.Setter
    public static class PeisGroupRosterImportRow {
        private PeisGroupRoster roster;
        private List<Long> optionalPackageIds;
    }
}
