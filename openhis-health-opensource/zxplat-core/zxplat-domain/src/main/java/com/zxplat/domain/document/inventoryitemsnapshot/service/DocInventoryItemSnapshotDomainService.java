/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.document.inventoryitemsnapshot.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.domain.document.inventoryitemsnapshot.command.BatchAppendDocInventoryItemSnapshotCommand;
import com.zxplat.domain.document.inventoryitemsnapshot.command.DocInventoryItemSnapshotRowCommand;
import com.zxplat.domain.document.inventoryitemsnapshot.entity.DocInventoryItemSnapshotEntity;
import com.zxplat.domain.document.inventoryitemsnapshot.repository.DocInventoryItemSnapshotRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 库存明细快照写侧领域服务（DM-DOC-001 · append-only）。
 *
 * <p>唯一写入口 {@link #batchAppendSnapshots}；禁止 UPDATE/DELETE API（BR-DOC-SNAP-003）。</p>
 * <p>{@code bus_no} 序号分配与日级 skip 判定在应用层 Pre 步骤完成，本服务不负责生成序号。</p>
 */
@Service
@RequiredArgsConstructor
public class DocInventoryItemSnapshotDomainService {

    private final DocInventoryItemSnapshotRepository repository;

    /**
     * 批量 INSERT 库存明细快照行（IF-10 · TC-DOC-SNAP-APPEND-001）。
     *
     * <p>前置：{@code rows} 已由应用层完成 IF-02 同等装配与字段映射（BR-DOC-SNAP-004）。</p>
     * <p>并发：同批次重复 {@code inventory_item_id} 依赖 Flyway UK
     * {@code (tenant_id, bus_no, inventory_item_id)} 保障最终一致性（BR-DOC-SNAP-002）。</p>
     *
     * @param cmd 批次命令
     * @return 写入行数；{@code rows} 为空时返回 0
     * @throws BusinessException 租户/批次/必填行字段非法，或同批次内存重复源库存项
     */
    public int batchAppendSnapshots(BatchAppendDocInventoryItemSnapshotCommand cmd) {
        // Pre：批次级必填项，防止无租户/无单号落库
        if (cmd == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_101, "快照命令不能为空");
        }
        if (cmd.getTenantId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_101, "租户不能为空");
        }
        String busNo = trimToNull(cmd.getBusNo());
        if (busNo == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_101, "批次单号不能为空");
        }
        if (cmd.getSnapshotTime() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_101, "快照时刻不能为空");
        }
        List<DocInventoryItemSnapshotRowCommand> rows = cmd.getRows();
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // Pre：逐行校验并映射为 append-only 实体
        List<DocInventoryItemSnapshotEntity> entities = new ArrayList<>();
        Set<Long> seenInventoryItemIds = new HashSet<>();
        for (DocInventoryItemSnapshotRowCommand row : rows) {
            if (row == null) {
                continue;
            }
            this.assertRowRequiredFields(row);
            // 同批次内源库存项不可重复，避免部分写入后 UK 冲突
            if (!seenInventoryItemIds.add(row.getInventoryItemId())) {
                throw new BusinessException(
                    ErrorCodeConstants.WHS_DTL_101,
                    "同批次存在重复源库存项：" + row.getInventoryItemId()
                );
            }
            entities.add(this.toEntity(cmd, busNo, row));
        }
        if (entities.isEmpty()) {
            return 0;
        }
        // Cmd：批量 INSERT（append-only，写库点 1；UK 保障并发最终一致，BR-DOC-SNAP-002）
        List<DocInventoryItemSnapshotEntity> saved = repository.saveAll(entities);
        return saved.size();
    }

    /**
     * 校验单行必填业务字段（BR-DOC-SNAP-004 / §4.2）。
     *
     * @param row 行命令
     */
    private void assertRowRequiredFields(DocInventoryItemSnapshotRowCommand row) {
        if (row.getInventoryItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_101, "源库存项不能为空");
        }
        if (StringUtils.isEmpty(row.getItemTable())) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_101, "物品表不能为空");
        }
        if (row.getItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_101, "物品主键不能为空");
        }
        if (row.getWarehouseId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_101, "库房不能为空");
        }
        if (row.getSupplierId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_101, "供应商不能为空");
        }
        if (StringUtils.isEmpty(row.getName())) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_101, "项目名称不能为空");
        }
        if (StringUtils.isEmpty(row.getLotNumber())) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_101, "生产批号不能为空");
        }
        if (row.getQuantity() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_101, "数量不能为空");
        }
        if (StringUtils.isEmpty(row.getInventoryStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_101, "供应状态不能为空");
        }
    }

    /**
     * 将行命令映射为待 INSERT 实体（仅事实列，BR-DOC-SNAP-005）。
     *
     * @param cmd   批次命令
     * @param busNo 已 trim 的批次单号
     * @param row   行命令
     * @return 新实体
     */
    private DocInventoryItemSnapshotEntity toEntity(
        BatchAppendDocInventoryItemSnapshotCommand cmd,
        String busNo,
        DocInventoryItemSnapshotRowCommand row
    ) {
        DocInventoryItemSnapshotEntity entity = new DocInventoryItemSnapshotEntity();
        entity.setTenantId(cmd.getTenantId());
        entity.setBusNo(busNo);
        entity.setSnapshotTime(cmd.getSnapshotTime());
        entity.setCreatedBy(cmd.getCreatedBy());
        entity.setInventoryItemId(row.getInventoryItemId());
        entity.setItemTable(row.getItemTable().trim());
        entity.setItemId(row.getItemId());
        entity.setChargeItemDefinitionId(row.getChargeItemDefinitionId());
        entity.setWarehouseId(row.getWarehouseId());
        entity.setSupplierId(row.getSupplierId());
        entity.setProductBusNo(trimToNull(row.getProductBusNo()));
        entity.setName(row.getName().trim());
        entity.setMerchandiseName(trimToNull(row.getMerchandiseName()));
        entity.setTotalVolume(trimToNull(row.getTotalVolume()));
        entity.setManufacturer(trimToNull(row.getManufacturer()));
        entity.setApprovalNumber(trimToNull(row.getApprovalNumber()));
        entity.setYbNo(trimToNull(row.getYbNo()));
        entity.setTraceNo(trimToNull(row.getTraceNo()));
        entity.setPyStr(trimToNull(row.getPyStr()));
        entity.setWbStr(trimToNull(row.getWbStr()));
        entity.setLotNumber(row.getLotNumber().trim());
        entity.setQuantity(row.getQuantity());
        entity.setPackageUnitRatio(row.getPackageUnitRatio());
        entity.setPackageUnitCode(trimToNull(row.getPackageUnitCode()));
        entity.setLotNumberCostAmount(row.getLotNumberCostAmount());
        entity.setLotNumberPriceAmount(row.getLotNumberPriceAmount());
        entity.setLineCostTotal(row.getLineCostTotal());
        entity.setLinePriceTotal(row.getLinePriceTotal());
        entity.setProductionDate(row.getProductionDate());
        entity.setExpirationDate(row.getExpirationDate());
        entity.setRemainingDays(row.getRemainingDays());
        entity.setInventoryStatusEnum(row.getInventoryStatusEnum().trim());
        entity.setChrgitmLv(row.getChrgitmLv());
        entity.setMedicationCategoryCode(trimToNull(row.getMedicationCategoryCode()));
        entity.setDeviceCategoryCode(trimToNull(row.getDeviceCategoryCode()));
        entity.setDoseFormCode(trimToNull(row.getDoseFormCode()));
        return entity;
    }
}
