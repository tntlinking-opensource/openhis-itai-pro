/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.inventoryopening;

import com.zxplat.domain.custom.tracenumber.service.TraceNumberDomainService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.SupplyType;
import com.zxplat.domain.workflow.inventoryitem.entity.InventoryItem;
import com.zxplat.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.zxplat.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.zxplat.domain.workflow.supplyrequest.command.VoidOpeningCommand;
import com.zxplat.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存期初作废/覆盖回滚门面（AS-BAS-INV-001 · GAP-OPN-003 · U4）。
 *
 * <p>同事务：按行 {@code decreaseQuantity} → 追溯 T1 {@code reverseHospitalIoInByBusNo} →
 * {@link SupplyRequestDomainService#voidOpening} 置失效。批号价不删不改。</p>
 *
 * <p>DOC-CONFLICT：AS/SDD 写 Domain {@code voidOpening} 内聚三聚合；工程 B.6 禁止 Domain 跨聚合，
 * 故本应用编排为 App 唯一入口，领域 {@code voidOpening} 仅供应状态迁移。</p>
 *
 * <p>读路径走 QueryService（S7：shared 禁止直连 Repository）。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryOpeningVoidOrchestrator {

    private final SupplyRequestQueryService supplyRequestQueryService;
    private final SupplyRequestDomainService supplyRequestDomainService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final InventoryItemDomainService inventoryItemDomainService;
    private final TraceNumberDomainService traceNumberDomainService;

    /**
     * 作废已同意期初单并回滚库存与追溯。
     *
     * @param busNo      期初单号
     * @param operatorId 操作人
     * @param reason     作废原因（可空）
     * @return 作废单号
     */
    @Transactional(rollbackFor = Exception.class)
    public String voidOpening(String busNo, Long operatorId, String reason) {
        // Gate：单号必填
        if (busNo == null || busNo.isBlank()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "busNo 不能为空");
        }
        String trimmed = busNo.trim();
        // Pre：按单号加载未软删供应行（读模型，禁止 shared 直连 Repository）
        SupplyByBusNoQuery busNoQuery = new SupplyByBusNoQuery();
        busNoQuery.setBusNo(trimmed);
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(busNoQuery);
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "期初单不存在");
        }
        SupplyRequestModel head = lines.get(0);
        // Gate：仅期初类型允许本编排回滚
        if (!SupplyType.isOpeningTypeCode(head.getTypeEnum())) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅期初单允许作废回滚");
        }

        // Cmd1：按期初行回滚库存数量（四元组命中）
        Map<Long, BigDecimal> decreaseByInventoryId = new HashMap<>();
        for (SupplyRequestModel line : lines) {
            // Skip：数量为空或非正，无可回滚量
            if (line.getItemQuantity() == null || line.getItemQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            List<InventoryItem> hits = inventoryItemQueryService.listInventoryItemEntitiesByLotLocationItem(
                line.getLotNumber(),
                line.getPurposeLocationId(),
                line.getItemTable(),
                line.getItemId());
            if (hits == null || hits.isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "作废回滚时未找到对应库存项：" + line.getLotNumber());
            }
            InventoryItem inv = hits.get(0);
            BigDecimal prev = decreaseByInventoryId.get(inv.getId());
            // Merge：同库存项多行数量合并后一次 decrease
            if (prev == null) {
                decreaseByInventoryId.put(inv.getId(), line.getItemQuantity());
            } else {
                decreaseByInventoryId.put(inv.getId(), prev.add(line.getItemQuantity()));
            }
        }
        if (!decreaseByInventoryId.isEmpty()) {
            inventoryItemDomainService.decreaseQuantitiesMerged(operatorId, decreaseByInventoryId);
        }

        // Cmd2：追溯 T1 冲销
        traceNumberDomainService.reverseHospitalIoInByBusNo(trimmed, operatorId, head.getTypeEnum());

        // Cmd3：供应单置失效
        VoidOpeningCommand voidCmd = new VoidOpeningCommand();
        voidCmd.setOperatorId(operatorId);
        VoidOpeningCommand.VoidOpeningItemCommand item = new VoidOpeningCommand.VoidOpeningItemCommand();
        item.setBusNo(trimmed);
        item.setReason(reason);
        voidCmd.setItem(item);
        return supplyRequestDomainService.voidOpening(voidCmd);
    }
}
