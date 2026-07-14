/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returntosupplier.support;

import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.support.SupplyPersistedSplitQuantity;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 药库退供应商与来源进货单之间的行键、可退余量计算（对齐 DM-WKF-006 {@code BR-SR-008}/{@code BR-SR-009}）。
 *
 * <p>行匹配键为 {@code itemTable + itemId + 归一化批号}；可退余量 = 进货拆零量 − 已占用退货量（待审/审中/已同意，修订时排除本单）。</p>
 */
public final class ReturnToSupplierInboundLineHelper {

    private ReturnToSupplierInboundLineHelper() {
    }

    /**
     * 构造进货/退货行匹配键（批号经库存可用量口径归一化）。
     *
     * @param itemTable 物品表
     * @param itemId    物品主键
     * @param lotNumber 批号，可 blank
     * @return 稳定行键
     */
    public static String buildInboundLineKey(String itemTable, Long itemId, String lotNumber) {
        if (StringUtils.isEmpty(itemTable) || itemId == null) {
            // 缺物品身份无法形成稳定行键，调用方应跳过或拒绝
            return "";
        }
        String lotPart = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(lotNumber);
        return itemTable.trim() + "|" + itemId + "|" + lotPart;
    }

    /**
     * 判断退货单状态是否占用来源进货的可退余量（驳回/撤回不计入）。
     *
     * @param statusEnumCode 供应请求状态持久化码
     * @return 是否计入已退占用量
     */
    public static boolean countsTowardReturnCommittedQuantity(String statusEnumCode) {
        if (StringUtils.isEmpty(statusEnumCode)) {
            return false;
        }
        if (SupplyStatus.REJECT.getCode().equals(statusEnumCode.trim())) {
            // 驳回单不占可退余量，可重新发起退货
            return false;
        }
        if (SupplyStatus.WITHDRAW.getCode().equals(statusEnumCode.trim())) {
            // 撤回草稿不计入已退占用
            return false;
        }
        return SupplyStatus.PENDING_APPROVAL.getCode().equals(statusEnumCode.trim())
            || SupplyStatus.APPROVAL.getCode().equals(statusEnumCode.trim())
            || SupplyStatus.AGREE.getCode().equals(statusEnumCode.trim());
    }

    /**
     * 将供应请求明细按行键汇总拆零数量。
     *
     * @param lines 供应请求读模型行
     * @return 行键 → 拆零数量合计
     */
    public static Map<String, BigDecimal> sumSplitQuantitiesByLineKey(List<SupplyRequestModel> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, BigDecimal> merged = new HashMap<>();
        for (SupplyRequestModel line : lines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            BigDecimal splitQty = line.getItemQuantity();
            if (splitQty == null || splitQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String key = buildInboundLineKey(line.getItemTable(), line.getItemId(), line.getLotNumber());
            if (key.isEmpty()) {
                continue;
            }
            merged.merge(key, splitQty, BigDecimal::add);
        }
        return merged;
    }

    /**
     * 将进货明细按行键索引（同键多行时保留首行供单位等头字段参照）。
     *
     * @param inboundLines 已同意进货单明细
     * @return 行键 → 代表进货行
     */
    public static Map<String, SupplyRequestModel> indexInboundLinesByKey(List<SupplyRequestModel> inboundLines) {
        Map<String, SupplyRequestModel> indexed = new LinkedHashMap<>();
        if (inboundLines == null) {
            return indexed;
        }
        for (SupplyRequestModel line : inboundLines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            String key = buildInboundLineKey(line.getItemTable(), line.getItemId(), line.getLotNumber());
            if (key.isEmpty()) {
                continue;
            }
            indexed.putIfAbsent(key, line);
        }
        return indexed;
    }

    /**
     * 计算各进货行当前可退余量（拆零单位）。
     *
     * @param inboundLines               来源进货单明细
     * @param returnsReferencingInbound  {@code original_bus_no} 指向该进货单的全部退货明细
     * @param excludeReturnBusNo         修订/同意复检时排除的本退货单号，可 blank
     * @return 行键 → 可退余量
     */
    public static Map<String, BigDecimal> computeReturnableQuantityByLineKey(
        List<SupplyRequestModel> inboundLines,
        List<SupplyRequestModel> returnsReferencingInbound,
        String excludeReturnBusNo) {
        Map<String, BigDecimal> inboundByKey = sumSplitQuantitiesByLineKey(inboundLines);
        Map<String, BigDecimal> committedReturnByKey = new HashMap<>();
        if (returnsReferencingInbound != null) {
            for (SupplyRequestModel line : returnsReferencingInbound) {
                if (line == null) {
                    continue;
                }
                if (!SupplyType.CABINET_RETURN_TO_SUPPLIER.getCode().equals(line.getTypeEnum())) {
                    // 只累计退供应商类型，忽略其它 original 引用
                    continue;
                }
                if (StringUtils.isNotEmpty(excludeReturnBusNo)
                    && excludeReturnBusNo.trim().equals(StringUtils.trimToEmpty(line.getBusNo()))) {
                    // 修订/同意复检时排除本单，避免把本单草稿重复计入已退量
                    continue;
                }
                if (!countsTowardReturnCommittedQuantity(line.getStatusEnum())) {
                    continue;
                }
                String key = buildInboundLineKey(line.getItemTable(), line.getItemId(), line.getLotNumber());
                if (key.isEmpty() || line.getItemQuantity() == null) {
                    continue;
                }
                committedReturnByKey.merge(key, line.getItemQuantity(), BigDecimal::add);
            }
        }
        Map<String, BigDecimal> returnable = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> inboundEntry : inboundByKey.entrySet()) {
            BigDecimal inboundQty = inboundEntry.getValue();
            BigDecimal returned = committedReturnByKey.getOrDefault(inboundEntry.getKey(), BigDecimal.ZERO);
            BigDecimal remaining = inboundQty.subtract(returned);
            if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                // 数据异常时钳制为 0，避免前端展示负可退量
                remaining = BigDecimal.ZERO;
            }
            returnable.put(inboundEntry.getKey(), remaining);
        }
        return returnable;
    }

    /**
     * 判断进货单是否仍存在至少一行可退余量 &gt; 0。
     *
     * @param returnableByKey 可退余量映射
     * @return 是否仍可发起退货
     */
    public static boolean inboundHasAnyReturnableLine(Map<String, BigDecimal> returnableByKey) {
        if (returnableByKey == null || returnableByKey.isEmpty()) {
            return false;
        }
        for (BigDecimal qty : returnableByKey.values()) {
            if (qty != null && qty.compareTo(BigDecimal.ZERO) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验草稿明细均来自进货行且数量不超过可退余量。
     *
     * @param inboundLines               来源进货明细
     * @param returnsReferencingInbound  已引用该进货单的退货明细
     * @param draftLines                 待保存退货行（拆零量）
     * @param excludeReturnBusNo         修订时排除的本单号
     * @param errorCode                  业务错误码
     * @throws BusinessException 行不在进货范围内或超可退余量
     */
    public static void assertDraftLinesWithinInboundReturnable(
        List<SupplyRequestModel> inboundLines,
        List<SupplyRequestModel> returnsReferencingInbound,
        List<DraftReturnLine> draftLines,
        String excludeReturnBusNo,
        String errorCode) {
        if (draftLines == null || draftLines.isEmpty()) {
            return;
        }
        Map<String, SupplyRequestModel> inboundByKey = indexInboundLinesByKey(inboundLines);
        Map<String, BigDecimal> returnableByKey = computeReturnableQuantityByLineKey(
            inboundLines, returnsReferencingInbound, excludeReturnBusNo);
        Map<String, BigDecimal> requestedByKey = new HashMap<>();
        for (DraftReturnLine draftLine : draftLines) {
            if (draftLine == null) {
                continue;
            }
            if (draftLine.getItemId() == null || StringUtils.isEmpty(draftLine.getItemTable())) {
                throw new BusinessException(errorCode, "退货明细须包含有效物品表与主键");
            }
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireMedicationOrDeviceTableAndPositiveSplitQuantity(
                draftLine.getItemTable().trim(),
                draftLine.getItemQuantity(),
                errorCode,
                "退货明细数量须为正数");
            String key = buildInboundLineKey(draftLine.getItemTable(), draftLine.getItemId(), draftLine.getLotNumber());
            if (!inboundByKey.containsKey(key)) {
                // 行不在进货单范围内，视为自由选品，直接拒绝
                throw new BusinessException(errorCode, "退货明细须来自关联进货单，禁止自由选品");
            }
            SupplyRequestModel inboundRef = inboundByKey.get(key);
            if (inboundRef != null && StringUtils.isNotEmpty(draftLine.getUnitCode())
                && StringUtils.isNotEmpty(inboundRef.getUnitCode())
                && !Objects.equals(draftLine.getUnitCode().trim(), inboundRef.getUnitCode().trim())) {
                throw new BusinessException(errorCode, "退货明细计量单位须与进货行一致");
            }
            requestedByKey.merge(key, splitQty, BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> requested : requestedByKey.entrySet()) {
            BigDecimal allowed = returnableByKey.getOrDefault(requested.getKey(), BigDecimal.ZERO);
            if (requested.getValue().compareTo(allowed) > 0) {
                // 超可退余量则拒绝，防止对同一进货行重复超量退货
                throw new BusinessException(errorCode, "退货数量超过该进货行可退余量");
            }
        }
    }

    /**
     * 草稿退货行最小字段（供 support 校验，避免依赖应用 DTO）。
     */
    public static final class DraftReturnLine {

        /** 物品来源表名。 */
        private String itemTable;
        /** 物品定义主键。 */
        private Long itemId;
        /** 退货拆零数量。 */
        private BigDecimal itemQuantity;
        /** 计量单位编码。 */
        private String unitCode;
        /** 批号。 */
        private String lotNumber;

        public DraftReturnLine() {
        }

        public DraftReturnLine(
            String itemTable,
            Long itemId,
            BigDecimal itemQuantity,
            String unitCode,
            String lotNumber) {
            this.itemTable = itemTable;
            this.itemId = itemId;
            this.itemQuantity = itemQuantity;
            this.unitCode = unitCode;
            this.lotNumber = lotNumber;
        }

        public String getItemTable() {
            return itemTable;
        }

        public void setItemTable(String itemTable) {
            this.itemTable = itemTable;
        }

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public BigDecimal getItemQuantity() {
            return itemQuantity;
        }

        public void setItemQuantity(BigDecimal itemQuantity) {
            this.itemQuantity = itemQuantity;
        }

        public String getUnitCode() {
            return unitCode;
        }

        public void setUnitCode(String unitCode) {
            this.unitCode = unitCode;
        }

        public String getLotNumber() {
            return lotNumber;
        }

        public void setLotNumber(String lotNumber) {
            this.lotNumber = lotNumber;
        }
    }
}
