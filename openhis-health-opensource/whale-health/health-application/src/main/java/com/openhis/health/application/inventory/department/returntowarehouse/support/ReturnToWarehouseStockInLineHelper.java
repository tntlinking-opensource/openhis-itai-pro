/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.support;

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
 * 科室退库与来源入库单之间的行键、可退余量计算（对齐 DM-WKF-006 {@code BR-SR-008}/{@code BR-SR-009}；type {@link SupplyType#DEPT_RETURN} 占用统计）。
 *
 * <p>行匹配键为 {@code itemTable + itemId + 归一化批号}；可退余量 = 入库拆零量 − 已占用退库量（待审/审中/已同意，修订时排除本单）。</p>
 */
public final class ReturnToWarehouseStockInLineHelper {

    private ReturnToWarehouseStockInLineHelper() {
    }

    /**
     * 构造入库/退库行匹配键（批号经库存可用量口径归一化）。
     *
     * @param itemTable 物品表
     * @param itemId    物品主键
     * @param lotNumber 批号，可 blank
     * @return 稳定行键
     */
    public static String buildStockInLineKey(String itemTable, Long itemId, String lotNumber) {
        if (StringUtils.isEmpty(itemTable) || itemId == null) {
            return "";
        }
        String lotPart = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(lotNumber);
        return itemTable.trim() + "|" + itemId + "|" + lotPart;
    }

    /**
     * 判断退库单状态是否占用来源入库的可退余量（驳回/撤回不计入）。
     *
     * @param statusEnumCode 供应请求状态持久化码
     * @return 是否计入已退占用量
     */
    public static boolean countsTowardReturnCommittedQuantity(String statusEnumCode) {
        if (StringUtils.isEmpty(statusEnumCode)) {
            return false;
        }
        if (SupplyStatus.REJECT.getCode().equals(statusEnumCode.trim())) {
            // 驳回单释放可退余量，不计入已退占用
            return false;
        }
        if (SupplyStatus.WITHDRAW.getCode().equals(statusEnumCode.trim())) {
            // 撤回单不计入已退占用
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
            String key = buildStockInLineKey(line.getItemTable(), line.getItemId(), line.getLotNumber());
            if (key.isEmpty()) {
                continue;
            }
            merged.merge(key, splitQty, BigDecimal::add);
        }
        return merged;
    }

    /**
     * 将入库明细按行键索引（同键多行时保留首行供单位等头字段参照）。
     *
     * @param stockInLines 已同意入库单明细
     * @return 行键 → 代表入库行
     */
    public static Map<String, SupplyRequestModel> indexStockInLinesByKey(List<SupplyRequestModel> stockInLines) {
        Map<String, SupplyRequestModel> indexed = new LinkedHashMap<>();
        if (stockInLines == null) {
            return indexed;
        }
        for (SupplyRequestModel line : stockInLines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            String key = buildStockInLineKey(line.getItemTable(), line.getItemId(), line.getLotNumber());
            if (key.isEmpty()) {
                continue;
            }
            indexed.putIfAbsent(key, line);
        }
        return indexed;
    }

    /**
     * 计算各入库行当前可退余量（拆零单位）。
     *
     * @param stockInLines               来源入库单明细
     * @param returnsReferencingStockIn  {@code original_bus_no} 指向该入库单的全部退库明细
     * @param excludeReturnBusNo         修订/同意复检时排除的本退库单号，可 blank
     * @return 行键 → 可退余量
     */
    public static Map<String, BigDecimal> computeReturnableQuantityByLineKey(
        List<SupplyRequestModel> stockInLines,
        List<SupplyRequestModel> returnsReferencingStockIn,
        String excludeReturnBusNo) {
        Map<String, BigDecimal> stockInByKey = sumSplitQuantitiesByLineKey(stockInLines);
        Map<String, BigDecimal> committedReturnByKey = new HashMap<>();
        if (returnsReferencingStockIn != null) {
            for (SupplyRequestModel line : returnsReferencingStockIn) {
                if (line == null) {
                    continue;
                }
                if (!SupplyType.DEPT_RETURN.getCode().equals(line.getTypeEnum())) {
                    // 仅统计科室退库单占用，忽略其它 original_bus_no 引用
                    continue;
                }
                if (StringUtils.isNotEmpty(excludeReturnBusNo)
                    && excludeReturnBusNo.trim().equals(StringUtils.trimToEmpty(line.getBusNo()))) {
                    // 修订/同意复检时排除本单已申报数量
                    continue;
                }
                if (!countsTowardReturnCommittedQuantity(line.getStatusEnum())) {
                    continue;
                }
                String key = buildStockInLineKey(line.getItemTable(), line.getItemId(), line.getLotNumber());
                if (key.isEmpty() || line.getItemQuantity() == null) {
                    continue;
                }
                committedReturnByKey.merge(key, line.getItemQuantity(), BigDecimal::add);
            }
        }
        Map<String, BigDecimal> returnable = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> stockInEntry : stockInByKey.entrySet()) {
            BigDecimal stockInQty = stockInEntry.getValue();
            BigDecimal returned = committedReturnByKey.getOrDefault(stockInEntry.getKey(), BigDecimal.ZERO);
            BigDecimal remaining = stockInQty.subtract(returned);
            if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                // 数据异常时钳制为 0，避免负可退余量外泄
                remaining = BigDecimal.ZERO;
            }
            returnable.put(stockInEntry.getKey(), remaining);
        }
        return returnable;
    }

    /**
     * 判断入库单是否仍存在至少一行可退余量 &gt; 0。
     *
     * @param returnableByKey 可退余量映射
     * @return 是否仍可发起退库
     */
    public static boolean stockInHasAnyReturnableLine(Map<String, BigDecimal> returnableByKey) {
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
     * 校验草稿明细均来自入库行且数量不超过可退余量。
     *
     * @param stockInLines               来源入库明细
     * @param returnsReferencingStockIn  已引用该入库单的退库明细
     * @param draftLines                 待保存退库行（拆零量）
     * @param excludeReturnBusNo         修订时排除的本单号
     * @param errorCode                  业务错误码
     * @throws BusinessException 行不在入库范围内或超可退余量
     */
    public static void assertDraftLinesWithinStockInReturnable(
        List<SupplyRequestModel> stockInLines,
        List<SupplyRequestModel> returnsReferencingStockIn,
        List<DraftReturnLine> draftLines,
        String excludeReturnBusNo,
        String errorCode) {
        if (draftLines == null || draftLines.isEmpty()) {
            return;
        }
        Map<String, SupplyRequestModel> stockInByKey = indexStockInLinesByKey(stockInLines);
        Map<String, BigDecimal> returnableByKey = computeReturnableQuantityByLineKey(
            stockInLines, returnsReferencingStockIn, excludeReturnBusNo);
        Map<String, BigDecimal> requestedByKey = new HashMap<>();
        for (DraftReturnLine draftLine : draftLines) {
            if (draftLine == null) {
                continue;
            }
            if (draftLine.getItemId() == null || StringUtils.isEmpty(draftLine.getItemTable())) {
                throw new BusinessException(errorCode, "退库明细须包含有效物品表与主键");
            }
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireMedicationOrDeviceTableAndPositiveSplitQuantity(
                draftLine.getItemTable().trim(),
                draftLine.getItemQuantity(),
                errorCode,
                "退库明细数量须为正数");
            String key = buildStockInLineKey(draftLine.getItemTable(), draftLine.getItemId(), draftLine.getLotNumber());
            if (!stockInByKey.containsKey(key)) {
                // 退库行须能匹配到入库行键，禁止自由选品
                throw new BusinessException(errorCode, "退库明细须来自关联入库单，禁止自由选品");
            }
            SupplyRequestModel stockInRef = stockInByKey.get(key);
            if (stockInRef != null && StringUtils.isNotEmpty(draftLine.getUnitCode())
                && StringUtils.isNotEmpty(stockInRef.getUnitCode())
                && !Objects.equals(draftLine.getUnitCode().trim(), stockInRef.getUnitCode().trim())) {
                // 计量单位须与入库行一致，防止拆零口径漂移
                throw new BusinessException(errorCode, "退库明细计量单位须与入库行一致");
            }
            requestedByKey.merge(key, splitQty, BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> requested : requestedByKey.entrySet()) {
            BigDecimal allowed = returnableByKey.getOrDefault(requested.getKey(), BigDecimal.ZERO);
            if (requested.getValue().compareTo(allowed) > 0) {
                // 同键多行汇总后不得超过可退余量
                throw new BusinessException(errorCode, "退库数量超过该入库行可退余量");
            }
        }
    }

    /**
     * 草稿退库行最小字段（供 support 校验，避免依赖应用 DTO）。
     */
    public static final class DraftReturnLine {

        /** 物品来源表名。 */
        private String itemTable;
        /** 物品定义主键。 */
        private Long itemId;
        /** 退库拆零数量。 */
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
