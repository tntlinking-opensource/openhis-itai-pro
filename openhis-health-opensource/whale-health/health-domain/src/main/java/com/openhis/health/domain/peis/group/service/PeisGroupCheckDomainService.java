/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.group.service;

import com.openhis.health.domain.peis.group.entity.PeisGroupBatch;
import com.openhis.health.domain.peis.group.entity.PeisGroupCompany;
import com.openhis.health.domain.peis.group.entity.PeisGroupContract;
import com.openhis.health.domain.peis.group.entity.PeisGroupContractPackage;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.peis.group.repository.PeisGroupBatchRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupCompanyRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupContractPackageRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupContractRepository;
import com.openhis.health.domain.peis.group.repository.PeisGroupRosterRepository;
import com.openhis.health.domain.share.constants.PeisGroupContractConstants;
import com.openhis.health.domain.share.constants.PeisGroupRosterConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 团检单位/合同/批次/名册写侧（DM-PEIS-GRP · AS-PEIS-GRP-001）。
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
    private final PeisGroupRosterRepository rosterRepository;

    /**
     * 保存团检单位（新建或更新）。
     *
     * @param patch      待写入字段；有 id 时走更新
     * @param operatorId 操作人
     * @return 持久化后的单位
     */
    public PeisGroupCompany saveCompany(PeisGroupCompany patch, Long operatorId) {
        PeisGroupCompany target = resolveCompanyForSave(patch);
        touch(target, operatorId);
        return companyRepository.save(target);
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

    public List<Long> listContractPackageIds(Long contractId) {
        return contractPackageRepository.findByContractIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(contractId)
            .stream()
            .map(PeisGroupContractPackage::getPackageActivityId)
            .toList();
    }

    private void replaceContractPackages(Long contractId, List<Long> packageActivityIds, Long operatorId) {
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
            throw new BusinessException("GROUP_CONTRACT_INVALID", "单位与套餐不能为空");
        }
        return List.copyOf(unique);
    }

    /**
     * 保存团检批次（新建或更新；更新时不改 statusCode）。
     *
     * @param patch      批次字段
     * @param operatorId 操作人
     * @return 持久化后的批次
     */
    public PeisGroupBatch saveBatch(PeisGroupBatch patch, Long operatorId) {
        PeisGroupBatch target = resolveBatchForSave(patch);
        if (target.getCompanyId() == null || target.getContractId() == null
            || target.getPackageActivityId() == null) {
            throw new BusinessException("GROUP_BATCH_INVALID", "单位、合同与套餐不能为空");
        }
        touch(target, operatorId);
        return batchRepository.save(target);
    }

    /**
     * 保存单条名册（导入前由调用方设置 batchId）。
     *
     * @param entity     名册行
     * @param operatorId 操作人
     * @return 持久化后的名册
     */
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

    /**
     * 批量导入名册（同患者同批次跳过）。
     *
     * @param batchId    批次主键
     * @param rows       待导入行
     * @param operatorId 操作人
     * @return 实际导入的名册主键列表
     */
    public List<Long> importRosterRows(Long batchId, List<PeisGroupRoster> rows, Long operatorId) {
        List<Long> importedIds = new ArrayList<>();
        for (PeisGroupRoster row : rows) {
            if (row.getPatientId() != null
                && rosterRepository.existsByBatchIdAndPatientIdAndIsDeletedFalse(batchId, row.getPatientId())) {
                continue;
            }
            row.setId(null);
            row.setBatchId(batchId);
            saveRoster(row, operatorId);
            importedIds.add(row.getId());
        }
        return importedIds;
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

    private PeisGroupRoster requireRoster(Long rosterId) {
        return rosterRepository.findByIdAndIsDeletedFalse(rosterId)
            .orElseThrow(() -> new BusinessException("ROSTER_NOT_FOUND", "该人员不在本批次名单"));
    }

    /**
     * 更新路径：加载托管实体后合并 patch 字段。
     *
     * @param patch 待写入字段
     * @return 可 save 的托管实体或新建实体
     */
    private PeisGroupCompany resolveCompanyForSave(PeisGroupCompany patch) {
        if (patch.getId() == null) {
            return patch;
        }
        PeisGroupCompany existing = companyRepository.findById(patch.getId())
            .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
            .orElseThrow(() -> new BusinessException("COMPANY_NOT_FOUND", "单位不存在"));
        existing.setName(patch.getName());
        existing.setOrgId(patch.getOrgId());
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
