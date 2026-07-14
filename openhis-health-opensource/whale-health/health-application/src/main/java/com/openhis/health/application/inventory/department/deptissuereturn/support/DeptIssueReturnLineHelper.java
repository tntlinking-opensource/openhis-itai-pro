/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissuereturn.support;

import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.support.SupplyPersistedSplitQuantity;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 科室退领与来源领用单之间的行键、可退余量计算（对齐 AS-INV-DEP-009；源类型 {@link SupplyType#DEPT_ISSUE}）。
 *
 * <p>行匹配键为 {@code itemTable + itemId + 归一化批号}（不含追溯码）；可退余量 = 领用拆零量 − 已占用退领量。</p>
 */
public final class DeptIssueReturnLineHelper {

    private DeptIssueReturnLineHelper() {
    }

    /**
     * 构造领用/退领行匹配键（批号经库存可用量口径归一化）。
     *
     * @param itemTable 物品表
     * @param itemId    物品主键
     * @param lotNumber 批号，可 blank
     * @return 稳定行键；字段缺失时返回空串
     */
    public static String buildIssueLineKey(String itemTable, Long itemId, String lotNumber) {
        if (StringUtils.isEmpty(itemTable) || itemId == null) {
            return "";
        }
        String lotPart = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(lotNumber);
        return itemTable.trim() + "|" + itemId + "|" + lotPart;
    }

    /**
     * 判断退领单状态是否占用来源领用的可退余量（驳回/撤回不计入）。
     *
     * @param statusEnumCode 供应请求状态持久化码
     * @return 是否计入已退占用量
     */
    public static boolean countsTowardReturnCommittedQuantity(String statusEnumCode) {
        if (StringUtils.isEmpty(statusEnumCode)) {
            return false;
        }
        if (SupplyStatus.REJECT.getCode().equals(statusEnumCode.trim())) {
            return false;
        }
        if (SupplyStatus.WITHDRAW.getCode().equals(statusEnumCode.trim())) {
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
            String key = buildIssueLineKey(line.getItemTable(), line.getItemId(), line.getLotNumber());
            if (key.isEmpty()) {
                continue;
            }
            merged.merge(key, splitQty, BigDecimal::add);
        }
        return merged;
    }

    /**
     * 将领用明细按行键索引（同键多行时保留首行供单位/追溯等参照）。
     *
     * @param issueLines 已同意领用单明细
     * @return 行键 → 代表领用行
     */
    public static Map<String, SupplyRequestModel> indexIssueLinesByKey(List<SupplyRequestModel> issueLines) {
        Map<String, SupplyRequestModel> indexed = new LinkedHashMap<>();
        if (issueLines == null) {
            return indexed;
        }
        for (SupplyRequestModel line : issueLines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            String key = buildIssueLineKey(line.getItemTable(), line.getItemId(), line.getLotNumber());
            if (key.isEmpty()) {
                continue;
            }
            indexed.putIfAbsent(key, line);
        }
        return indexed;
    }

    /**
     * 计算各领用行当前可退余量（拆零单位）。
     *
     * @param issueLines               来源领用单明细
     * @param returnsReferencingIssue  {@code original_bus_no} 指向该领用单的全部退领明细
     * @param excludeReturnBusNo         修订/同意复检时排除的本退领单号，可 blank
     * @return 行键 → 可退余量
     */
    public static Map<String, BigDecimal> computeReturnableQuantityByLineKey(
        List<SupplyRequestModel> issueLines,
        List<SupplyRequestModel> returnsReferencingIssue,
        String excludeReturnBusNo) {
        Map<String, BigDecimal> issueByKey = sumSplitQuantitiesByLineKey(issueLines);
        Map<String, BigDecimal> committedReturnByKey = new HashMap<>();
        if (returnsReferencingIssue != null) {
            for (SupplyRequestModel line : returnsReferencingIssue) {
                if (line == null) {
                    continue;
                }
                if (!SupplyType.DEPT_ISSUE_RETURN.getCode().equals(line.getTypeEnum())) {
                    continue;
                }
                if (StringUtils.isNotEmpty(excludeReturnBusNo)
                    && excludeReturnBusNo.trim().equals(StringUtils.trimToEmpty(line.getBusNo()))) {
                    continue;
                }
                if (!countsTowardReturnCommittedQuantity(line.getStatusEnum())) {
                    continue;
                }
                String key = buildIssueLineKey(line.getItemTable(), line.getItemId(), line.getLotNumber());
                if (key.isEmpty() || line.getItemQuantity() == null) {
                    continue;
                }
                committedReturnByKey.merge(key, line.getItemQuantity(), BigDecimal::add);
            }
        }
        Map<String, BigDecimal> returnable = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> issueEntry : issueByKey.entrySet()) {
            BigDecimal issueQty = issueEntry.getValue();
            BigDecimal returned = committedReturnByKey.getOrDefault(issueEntry.getKey(), BigDecimal.ZERO);
            BigDecimal remaining = issueQty.subtract(returned);
            if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                remaining = BigDecimal.ZERO;
            }
            returnable.put(issueEntry.getKey(), remaining);
        }
        return returnable;
    }

    /**
     * 判断领用单是否仍存在至少一行可退余量 &gt; 0。
     *
     * @param returnableByKey 可退余量映射
     * @return 是否仍可发起退领
     */
    public static boolean issueHasAnyReturnableLine(Map<String, BigDecimal> returnableByKey) {
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
     * 按领用明细构建可退行快照（拉取预填 IF-DEP-TL-13）。
     *
     * @param issueLines               来源领用明细
     * @param returnsReferencingIssue  已引用该领用单的退领明细
     * @param excludeReturnBusNo         修订时排除的本单号，可 blank
     * @return 可退行快照列表（含可退余量为 0 的行，由前端决定是否展示）
     */
    public static List<ReturnableLineSnapshot> buildReturnableLines(
        List<SupplyRequestModel> issueLines,
        List<SupplyRequestModel> returnsReferencingIssue,
        String excludeReturnBusNo) {
        List<ReturnableLineSnapshot> snapshots = new ArrayList<>();
        if (issueLines == null || issueLines.isEmpty()) {
            return snapshots;
        }
        Map<String, BigDecimal> returnableByKey = computeReturnableQuantityByLineKey(
            issueLines, returnsReferencingIssue, excludeReturnBusNo);
        Map<String, BigDecimal> issueByKey = sumSplitQuantitiesByLineKey(issueLines);
        Map<String, SupplyRequestModel> issueRefByKey = indexIssueLinesByKey(issueLines);
        for (Map.Entry<String, SupplyRequestModel> entry : issueRefByKey.entrySet()) {
            String key = entry.getKey();
            SupplyRequestModel ref = entry.getValue();
            if (ref == null) {
                continue;
            }
            ReturnableLineSnapshot snap = new ReturnableLineSnapshot();
            snap.setItemTable(ref.getItemTable());
            snap.setItemId(ref.getItemId());
            snap.setLotNumber(ref.getLotNumber());
            snap.setTraceNo(ref.getTraceNo());
            snap.setIssueQuantity(issueByKey.getOrDefault(key, BigDecimal.ZERO));
            snap.setReturnableQuantity(returnableByKey.getOrDefault(key, BigDecimal.ZERO));
            snap.setUnitCode(ref.getUnitCode());
            snap.setPrice(ref.getPrice());
            snapshots.add(snap);
        }
        return snapshots;
    }

    /**
     * 校验草稿明细均来自领用行且数量不超过可退余量（含追溯码同码约束）。
     *
     * @param issueLines               来源领用明细
     * @param returnsReferencingIssue  已引用该领用单的退领明细
     * @param draftLines                 待保存退领行
     * @param excludeReturnBusNo         修订时排除的本单号
     * @param errorCode                  业务错误码
     * @throws BusinessException 行不在领用范围内、超可退余量或追溯码不一致
     */
    public static void assertDraftLinesWithinReturnable(
        List<SupplyRequestModel> issueLines,
        List<SupplyRequestModel> returnsReferencingIssue,
        List<DraftReturnLine> draftLines,
        String excludeReturnBusNo,
        String errorCode) {
        if (draftLines == null || draftLines.isEmpty()) {
            return;
        }
        Map<String, SupplyRequestModel> issueByKey = indexIssueLinesByKey(issueLines);
        Map<String, BigDecimal> returnableByKey = computeReturnableQuantityByLineKey(
            issueLines, returnsReferencingIssue, excludeReturnBusNo);
        Map<String, BigDecimal> requestedByKey = new HashMap<>();
        for (DraftReturnLine draftLine : draftLines) {
            if (draftLine == null) {
                continue;
            }
            if (draftLine.getItemId() == null || StringUtils.isEmpty(draftLine.getItemTable())) {
                throw new BusinessException(errorCode, "退领明细须包含有效物品表与主键");
            }
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireMedicationOrDeviceTableAndPositiveSplitQuantity(
                draftLine.getItemTable().trim(),
                draftLine.getItemQuantity(),
                errorCode,
                "退领明细数量须为正数");
            String key = buildIssueLineKey(draftLine.getItemTable(), draftLine.getItemId(), draftLine.getLotNumber());
            if (!issueByKey.containsKey(key)) {
                throw new BusinessException(errorCode, "退领明细须来自关联领用单，禁止自由选品");
            }
            SupplyRequestModel issueRef = issueByKey.get(key);
            if (issueRef != null && StringUtils.isNotEmpty(draftLine.getUnitCode())
                && StringUtils.isNotEmpty(issueRef.getUnitCode())
                && !Objects.equals(draftLine.getUnitCode().trim(), issueRef.getUnitCode().trim())) {
                throw new BusinessException(errorCode, "退领明细计量单位须与领用行一致");
            }
            if (issueRef != null && StringUtils.isNotEmpty(issueRef.getTraceNo())) {
                String sourceTrace = issueRef.getTraceNo().trim();
                if (StringUtils.isEmpty(draftLine.getTraceNo())) {
                    throw new BusinessException(errorCode, "源领用行有追溯码时退领行须填写相同追溯码");
                }
                if (!sourceTrace.equals(draftLine.getTraceNo().trim())) {
                    throw new BusinessException(errorCode, "退领追溯码须与源领用行一致");
                }
            }
            requestedByKey.merge(key, splitQty, BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> requested : requestedByKey.entrySet()) {
            BigDecimal allowed = returnableByKey.getOrDefault(requested.getKey(), BigDecimal.ZERO);
            if (requested.getValue().compareTo(allowed) > 0) {
                throw new BusinessException(errorCode, "退领数量超过该领用行可退余量");
            }
        }
    }

    /**
     * 可退行快照（供拉取预填，避免 support 依赖应用 DTO）。
     */
    public static final class ReturnableLineSnapshot {

        /** 物品来源表名。 */
        private String itemTable;
        /** 物品定义主键。 */
        private Long itemId;
        /** 批号。 */
        private String lotNumber;
        /** 源领用追溯码。 */
        private String traceNo;
        /** 源领用数量（拆零）。 */
        private BigDecimal issueQuantity;
        /** 当前可退余量（拆零）。 */
        private BigDecimal returnableQuantity;
        /** 计量单位编码。 */
        private String unitCode;
        /** 源行落库单价。 */
        private BigDecimal price;

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

        public String getLotNumber() {
            return lotNumber;
        }

        public void setLotNumber(String lotNumber) {
            this.lotNumber = lotNumber;
        }

        public String getTraceNo() {
            return traceNo;
        }

        public void setTraceNo(String traceNo) {
            this.traceNo = traceNo;
        }

        public BigDecimal getIssueQuantity() {
            return issueQuantity;
        }

        public void setIssueQuantity(BigDecimal issueQuantity) {
            this.issueQuantity = issueQuantity;
        }

        public BigDecimal getReturnableQuantity() {
            return returnableQuantity;
        }

        public void setReturnableQuantity(BigDecimal returnableQuantity) {
            this.returnableQuantity = returnableQuantity;
        }

        public String getUnitCode() {
            return unitCode;
        }

        public void setUnitCode(String unitCode) {
            this.unitCode = unitCode;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }
    }

    /**
     * 草稿退领行最小字段（供 support 校验，避免依赖应用 DTO）。
     */
    public static final class DraftReturnLine {

        /** 物品来源表名。 */
        private String itemTable;
        /** 物品定义主键。 */
        private Long itemId;
        /** 退领拆零数量。 */
        private BigDecimal itemQuantity;
        /** 计量单位编码。 */
        private String unitCode;
        /** 批号。 */
        private String lotNumber;
        /** 追溯码。 */
        private String traceNo;

        public DraftReturnLine() {
        }

        public DraftReturnLine(
            String itemTable,
            Long itemId,
            BigDecimal itemQuantity,
            String unitCode,
            String lotNumber,
            String traceNo) {
            this.itemTable = itemTable;
            this.itemId = itemId;
            this.itemQuantity = itemQuantity;
            this.unitCode = unitCode;
            this.lotNumber = lotNumber;
            this.traceNo = traceNo;
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

        public String getTraceNo() {
            return traceNo;
        }

        public void setTraceNo(String traceNo) {
            this.traceNo = traceNo;
        }
    }
}
