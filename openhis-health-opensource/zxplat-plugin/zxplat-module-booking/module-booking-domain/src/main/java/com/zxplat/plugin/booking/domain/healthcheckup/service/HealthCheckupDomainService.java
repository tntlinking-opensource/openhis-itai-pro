/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.healthcheckup.service;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.GroupCheckupBatch;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.GroupCheckupOrg;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.GroupCheckupRoster;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.HealthCheckBookingExt;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.HealthCheckPackage;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.HealthCheckPackageItem;
import com.zxplat.plugin.booking.domain.healthcheckup.repository.GroupCheckupBatchRepository;
import com.zxplat.plugin.booking.domain.healthcheckup.repository.GroupCheckupOrgRepository;
import com.zxplat.plugin.booking.domain.healthcheckup.repository.GroupCheckupRosterRepository;
import com.zxplat.plugin.booking.domain.healthcheckup.repository.HealthCheckBookingExtRepository;
import com.zxplat.plugin.booking.domain.healthcheckup.repository.HealthCheckPackageItemRepository;
import com.zxplat.plugin.booking.domain.healthcheckup.repository.HealthCheckPackageRepository;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 体检/团检主数据与预约扩展写侧。
 *
 * <p>边界：套餐明细全量替换、团检批次容量计数收口在本服务。</p>
 */
@Service
@RequiredArgsConstructor
public class HealthCheckupDomainService {

    private final GroupCheckupOrgRepository orgRepository;
    private final GroupCheckupBatchRepository batchRepository;
    private final GroupCheckupRosterRepository rosterRepository;
    private final HealthCheckPackageRepository packageRepository;
    private final HealthCheckPackageItemRepository packageItemRepository;
    private final HealthCheckBookingExtRepository bookingExtRepository;

    /**
     * 保存团检单位。
     *
     * @param entity 单位实体
     * @param operatorId 操作人
     * @return 落库后的实体
     */
    public GroupCheckupOrg saveOrg(GroupCheckupOrg entity, Long operatorId) {
        return this.saveAudited(entity, operatorId, orgRepository::save);
    }

    /**
     * 软删团检单位。
     *
     * @param id 单位主键
     * @param operatorId 操作人
     */
    public void deleteOrg(Long id, Long operatorId) {
        this.softDelete(id, operatorId, orgRepository);
    }

    /**
     * 保存团检批次。
     *
     * @param entity 批次实体
     * @param operatorId 操作人
     * @return 落库后的实体
     */
    public GroupCheckupBatch saveBatch(GroupCheckupBatch entity, Long operatorId) {
        return this.saveAudited(entity, operatorId, batchRepository::save);
    }

    /**
     * 软删团检批次。
     *
     * @param id 批次主键
     * @param operatorId 操作人
     */
    public void deleteBatch(Long id, Long operatorId) {
        this.softDelete(id, operatorId, batchRepository);
    }

    /**
     * 保存团检花名册行。
     *
     * @param entity 花名册实体
     * @param operatorId 操作人
     * @return 落库后的实体
     */
    public GroupCheckupRoster saveRoster(GroupCheckupRoster entity, Long operatorId) {
        return this.saveAudited(entity, operatorId, rosterRepository::save);
    }

    /**
     * 软删团检花名册行。
     *
     * @param id 花名册主键
     * @param operatorId 操作人
     */
    public void deleteRoster(Long id, Long operatorId) {
        this.softDelete(id, operatorId, rosterRepository);
    }

    /**
     * 保存体检套餐头。
     *
     * @param entity 套餐实体
     * @param operatorId 操作人
     * @return 落库后的实体
     */
    public HealthCheckPackage savePackage(HealthCheckPackage entity, Long operatorId) {
        return this.saveAudited(entity, operatorId, packageRepository::save);
    }

    /**
     * 软删体检套餐。
     *
     * @param id 套餐主键
     * @param operatorId 操作人
     */
    public void deletePackage(Long id, Long operatorId) {
        this.softDelete(id, operatorId, packageRepository);
    }

    /**
     * 保存套餐明细行。
     *
     * @param entity 明细实体
     * @param operatorId 操作人
     * @return 落库后的实体
     */
    public HealthCheckPackageItem savePackageItem(HealthCheckPackageItem entity, Long operatorId) {
        return this.saveAudited(entity, operatorId, packageItemRepository::save);
    }

    /**
     * 软删套餐明细行。
     *
     * @param id 明细主键
     * @param operatorId 操作人
     */
    public void deletePackageItem(Long id, Long operatorId) {
        this.softDelete(id, operatorId, packageItemRepository);
    }

    /**
     * 全量替换套餐明细（先软删旧行再插入新行）。
     *
     * @param packageId 套餐主键
     * @param items 新明细列表
     * @param operatorId 操作人
     * @throws BusinessException packageId 为空时
     */
    public void replacePackageItems(Long packageId, List<HealthCheckPackageItem> items, Long operatorId) {
        if (packageId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "packageId 不能为空");
        }
        List<HealthCheckPackageItem> existing = packageItemRepository.findByPackageIdAndIsDeletedFalseOrderBySortOrderAsc(packageId);
        // 软删既有明细，保证套餐下仅一套有效明细
        for (HealthCheckPackageItem row : existing) {
            row.setIsDeleted(Boolean.TRUE);
            row.setDeletedAt(LocalDateTime.now());
            row.setDeletedBy(operatorId);
        }
        if (!existing.isEmpty()) {
            packageItemRepository.saveAll(existing);
        }
        if (items != null) {
            for (HealthCheckPackageItem item : items) {
                item.setPackageId(packageId);
                item.setId(null);
                this.savePackageItem(item, operatorId);
            }
        }
    }

    /**
     * 保存体检预约扩展字段。
     *
     * @param entity 扩展实体
     * @param operatorId 操作人
     * @return 落库后的实体
     */
    public HealthCheckBookingExt saveBookingExt(HealthCheckBookingExt entity, Long operatorId) {
        return this.saveAudited(entity, operatorId, bookingExtRepository::save);
    }

    /**
     * 增减团检批次已预约人数（占号/释号时调用）。
     *
     * @param batchId 批次主键
     * @param delta 增量（释号为负）
     * @throws BusinessException 批次不存在或超出容量上限时
     */
    public void incrementBatchBookedCount(Long batchId, int delta) {
        // 无变更或无效批次时静默返回
        if (batchId == null || delta == 0) {
            return;
        }
        GroupCheckupBatch batch = batchRepository.findById(batchId).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "团检批次不存在"));
        int next = batch.getBookedCount() + delta;
        // 释号后计数不得为负
        if (next < 0) {
            next = 0;
        }
        // 有容量上限时禁止超卖
        if (batch.getCapacityLimit() != null && batch.getCapacityLimit() > 0 && next > batch.getCapacityLimit()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "团检批次容量已满");
        }
        batch.setBookedCount(next);
        batchRepository.save(batch);
    }

    private <T extends com.zxplat.framework.core.domain.BaseAuditedEntity> T saveAudited(
        T entity, Long operatorId, java.util.function.Function<T, T> saver) {
        LocalDateTime now = LocalDateTime.now();
        if (entity.getId() == null) {
            entity.setCreatedAt(now);
            entity.setCreatedBy(operatorId);
            entity.setIsDeleted(Boolean.FALSE);
        }
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operatorId);
        return saver.apply(entity);
    }

    private <T extends com.zxplat.framework.core.domain.BaseAuditedEntity> void softDelete(
        Long id, Long operatorId, org.springframework.data.jpa.repository.JpaRepository<T, Long> repository) {
        repository.findById(id).ifPresent(entity -> {
            entity.setIsDeleted(Boolean.TRUE);
            entity.setDeletedAt(LocalDateTime.now());
            entity.setDeletedBy(operatorId);
            repository.save(entity);
        });
    }
}
