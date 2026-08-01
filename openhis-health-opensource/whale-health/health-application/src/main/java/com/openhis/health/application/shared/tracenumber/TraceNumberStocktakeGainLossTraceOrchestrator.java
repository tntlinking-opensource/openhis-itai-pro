/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.tracenumber;

import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.custom.tracenumber.queries.model.InventoryItemTraceNumberQueryModel;
import com.openhis.health.domain.custom.tracenumber.queries.model.TraceNumberQueryModel;
import com.openhis.health.domain.custom.tracenumber.service.TraceNumberQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.InHospitalStatus;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 盘点/损益 agree 路径追溯写编排 Support（AS-CUS-TRC-001 · IF-TRC-14）。
 *
 * <p><strong>盘点</strong>：行上 {@code trace_no} 存实盘<strong>剩余码</strong>；与本库
 * {@link TraceNumberQueryService#listInStockTraceNumbersByInventoryItemKey} 差分 →
 * 未扫到 OUT（盘亏）、多扫到 IN（盘盈）。未扫剩余码则追溯不变（不默认全出）。</p>
 *
 * <p><strong>损益</strong>：行上 {@code trace_no} 存盈亏码本身；与 {@code item_quantity} 正负同向直接
 * {@code HOSPITAL_IO} IN（报溢）/ OUT（报损），不做剩余码差分。</p>
 */
@Component
@RequiredArgsConstructor
public class TraceNumberStocktakeGainLossTraceOrchestrator {

    /** 本库在库码读侧（IF-TRC-22 能力，B.6.4 经 QS 跨聚合读）。 */
    private final TraceNumberQueryService traceNumberQueryService;

    /** 库位类型解析。 */
    private final LocationQueryService locationQueryService;

    /** 逗号串拆分与供应写编排。 */
    private final TraceNumberRecordOrchestrator traceNumberRecordOrchestrator;

    /** 单端供应行写编排（OUT/IN 落库）。 */
    private final TraceNumberSupplyAgreeTraceOrchestrator traceNumberSupplyAgreeTraceOrchestrator;

    /**
     * 批量处理已同意<strong>盘点</strong>明细行（IF-TRC-14 · 剩余码差分）。
     *
     * @param agreedLines            已同意供应行
     * @param operatorId             操作人
     * @param productByCompositeKey  品项复合键 → 产品读模型
     * @param agreeInventoryErrorCode 库存 agree 失败码（追溯失败时复用并前缀说明）
     * @throws BusinessException 差分或落库失败时，包装为 {@code agreeInventoryErrorCode}
     */
    public void recordFromAgreedLines(
        List<SupplyRequestModel> agreedLines,
        Long operatorId,
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey,
        String agreeInventoryErrorCode
    ) {
        if (agreedLines == null || agreedLines.isEmpty()) {
            return;
        }
        Map<String, ProductDefinitionDisplayDto> safeProductMap = productByCompositeKey;
        if (safeProductMap == null) {
            safeProductMap = Map.of();
        }
        List<RecordTraceFromSupplyLineSpec> pending = new ArrayList<>();
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            // 零盈亏行不写追溯；仅药品行参与盘点差分
            BigDecimal profitLossQty = line.getItemQuantity();
            if (profitLossQty == null || profitLossQty.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(trimToNull(line.getItemTable()))) {
                continue;
            }
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto product = safeProductMap.get(productKey);
            RecordTraceFromStocktakeGainLossLineSpec lineCmd = this.buildLineCommand(line, operatorId, product);
            List<RecordTraceFromSupplyLineSpec> lineWrites =
                this.buildSupplyWriteCommands(lineCmd);
            pending.addAll(lineWrites);
        }
        if (pending.isEmpty()) {
            return;
        }
        try {
            // Cmd：批量单端 HOSPITAL_IO OUT/IN 落主/子表（盘点差分结果）
            traceNumberSupplyAgreeTraceOrchestrator.recordSingleEndLines(pending);
        } catch (BusinessException ex) {
            throw new BusinessException(agreeInventoryErrorCode, "追溯码盘点记录失败：" + ex.getMessage());
        }
    }

    /**
     * 批量处理已同意<strong>损益</strong>明细行（IF-TRC-14 · 盈亏码直接加减）。
     *
     * <p>行 {@code trace_no} 为报溢/报损扫到的码；{@code item_quantity} &gt; 0 → IN，
     * &lt; 0 → OUT；数量取盈亏绝对值，按码均分。无码行跳过。</p>
     *
     * @param agreedLines            已同意供应行
     * @param operatorId             操作人
     * @param productByCompositeKey  品项复合键 → 产品读模型
     * @param agreeInventoryErrorCode 库存 agree 失败码（追溯失败时复用并前缀说明）
     * @throws BusinessException 落库失败时，包装为 {@code agreeInventoryErrorCode}
     */
    public void recordDirectFromAgreedGainLossLines(
        List<SupplyRequestModel> agreedLines,
        Long operatorId,
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey,
        String agreeInventoryErrorCode
    ) {
        if (agreedLines == null || agreedLines.isEmpty()) {
            return;
        }
        Map<String, ProductDefinitionDisplayDto> safeProductMap = productByCompositeKey;
        if (safeProductMap == null) {
            safeProductMap = Map.of();
        }
        List<RecordTraceFromSupplyLineSpec> pending = new ArrayList<>();
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            // 零盈亏或不扫码：不写追溯
            BigDecimal profitLossQty = line.getItemQuantity();
            if (profitLossQty == null || profitLossQty.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            if (trimToNull(line.getTraceNo()) == null) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(trimToNull(line.getItemTable()))) {
                continue;
            }
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto product = safeProductMap.get(productKey);
            RecordTraceFromStocktakeGainLossLineSpec lineCmd = this.buildLineCommand(line, operatorId, product);
            RecordTraceFromSupplyLineSpec writeCmd = this.buildDirectGainLossSupplyCommand(lineCmd);
            pending.add(writeCmd);
        }
        if (pending.isEmpty()) {
            return;
        }
        try {
            // Cmd：批量单端 HOSPITAL_IO 直接 IN/OUT（与损益正负同向）
            traceNumberSupplyAgreeTraceOrchestrator.recordSingleEndLines(pending);
        } catch (BusinessException ex) {
            throw new BusinessException(agreeInventoryErrorCode, "追溯码损益记录失败：" + ex.getMessage());
        }
    }

    /**
     * 由损益行命令组装单端供应写命令（正 → IN，负 → OUT，数量取绝对值）。
     *
     * @param cmd 损益写编排命令（含扫码与盈亏拆零量）
     * @return 待落库的供应写命令
     */
    private RecordTraceFromSupplyLineSpec buildDirectGainLossSupplyCommand(
        RecordTraceFromStocktakeGainLossLineSpec cmd
    ) {
        RecordTraceFromSupplyLineSpec writeCmd = this.buildBaseSupplyCommand(cmd);
        writeCmd.setTraceNos(cmd.getScannedTraceNos());
        writeCmd.setQuantity(cmd.getProfitLossSplitQuantity().abs());
        // 报损：扫少的码直接 OUT；报溢：扫多的码直接 IN
        if (cmd.getProfitLossSplitQuantity().compareTo(BigDecimal.ZERO) < 0) {
            writeCmd.setRecordDirectionEnum(TraceDirection.OUT.getCode());
        } else {
            writeCmd.setRecordDirectionEnum(TraceDirection.IN.getCode());
            writeCmd.setTraceUnitCode(this.resolveTraceUnitCode(cmd));
        }
        return writeCmd;
    }

    /**
     * 单行差分构建供应写命令列表（可能多条 OUT/IN）。
     *
     * @param cmd 盘点/损益写编排命令
     * @return 待落库的供应写命令；无在库码且无盈余新码时为空列表
     * @throws BusinessException 批号/单位非法、扫码不在本库账、出库量超盈亏等
     */
    public List<RecordTraceFromSupplyLineSpec> buildSupplyWriteCommands(
        RecordTraceFromStocktakeGainLossLineSpec cmd
    ) {
        List<RecordTraceFromSupplyLineSpec> result = new ArrayList<>();
        if (cmd == null || cmd.getLocationId() == null || cmd.getItemId() == null) {
            return result;
        }
        if (cmd.getUnitConversionSource() == null) {
            throw new BusinessException(
                ErrorCodeConstants.TRACE_NUMBER_INVALID_UNIT,
                "药品单位换算源不能为空，须由应用层预加载后填入");
        }
        String resolvedLot = trimToNull(cmd.getLotNumber());
        if (resolvedLot == null) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "批号不能为空");
        }
        // Pre：读本库在库码（子表轧差净量 > 0，IF-TRC-22）
        List<InventoryItemTraceNumberQueryModel> inStockRows =
            traceNumberQueryService.listInStockTraceNumbersByInventoryItemKey(
                cmd.getLocationId(),
                TableNameConstants.MED_MEDICATION_DEFINITION,
                cmd.getItemId(),
                resolvedLot
            );
        Map<String, InventoryItemTraceNumberQueryModel> inStockByCode = new HashMap<>();
        Set<String> inStockCodes = new HashSet<>();
        for (InventoryItemTraceNumberQueryModel row : inStockRows) {
            if (row == null || trimToNull(row.getTraceNo()) == null) {
                continue;
            }
            String code = row.getTraceNo().trim();
            inStockByCode.put(code, row);
            inStockCodes.add(code);
        }
        Set<String> scannedCodes = new HashSet<>(
            traceNumberRecordOrchestrator.splitTraceNos(cmd.getScannedTraceNos()));
        BigDecimal profitLossQty = cmd.getProfitLossSplitQuantity();
        if (profitLossQty.compareTo(BigDecimal.ZERO) < 0) {
            // 未扫剩余码：追溯不变（不默认全出、也不强制扫）
            if (scannedCodes.isEmpty()) {
                return result;
            }
            // Gate：剩余扫码须在本库账上，防止扫外库码
            for (String scanned : scannedCodes) {
                if (!inStockCodes.contains(scanned)) {
                    throw new BusinessException(
                        ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT,
                        "剩余扫码不在本库在库追溯码列表中：" + scanned);
                }
            }
        }
        if (profitLossQty.compareTo(BigDecimal.ZERO) < 0) {
            // 本库无在库码则无需写 OUT
            if (inStockCodes.isEmpty()) {
                return result;
            }
            // 盘亏：未扫到的在库码整码 OUT（数量取本库净拆零量）
            for (String inStockCode : inStockCodes) {
                if (scannedCodes.contains(inStockCode)) {
                    continue;
                }
                InventoryItemTraceNumberQueryModel inStockRow = inStockByCode.get(inStockCode);
                BigDecimal netSplit = inStockRow.getLocationNetSplitQuantity();
                if (netSplit == null || netSplit.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                RecordTraceFromSupplyLineSpec outCmd = this.buildBaseSupplyCommand(cmd);
                outCmd.setTraceNos(inStockCode);
                outCmd.setQuantity(netSplit);
                outCmd.setRecordDirectionEnum(TraceDirection.OUT.getCode());
                result.add(outCmd);
            }
            this.assertLossSplitQuantityWithinProfitLoss(result, profitLossQty.abs());
            return result;
        }
        if (profitLossQty.compareTo(BigDecimal.ZERO) > 0) {
            // 盘盈/报溢：扫到但不在本库账上的码视为盈余新码
            List<String> surplusCodes = new ArrayList<>();
            for (String scanned : scannedCodes) {
                if (!inStockCodes.contains(scanned)) {
                    surplusCodes.add(scanned);
                }
            }
            if (surplusCodes.isEmpty()) {
                return result;
            }
            // 多码盘盈时按码均分行业务拆零量
            BigDecimal perCodeQty = profitLossQty;
            if (surplusCodes.size() > 1) {
                perCodeQty = profitLossQty.divide(
                    BigDecimal.valueOf(surplusCodes.size()), 6, RoundingMode.HALF_UP);
            }
            for (String surplusCode : surplusCodes) {
                TraceNumberQueryModel master = traceNumberQueryService.getTraceNumberByTraceNo(surplusCode);
                // 已在院的主档不可重复 IN
                if (master != null
                    && InHospitalStatus.IN_HOSPITAL.getCode().equals(master.getInHospitalStatusEnum())) {
                    throw new BusinessException(
                        ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT,
                        "盘盈新码已在院，不可重复入库：" + surplusCode);
                }
                RecordTraceFromSupplyLineSpec inCmd = this.buildBaseSupplyCommand(cmd);
                inCmd.setTraceNos(surplusCode);
                inCmd.setQuantity(perCodeQty);
                inCmd.setTraceUnitCode(this.resolveTraceUnitCode(cmd));
                inCmd.setRecordDirectionEnum(TraceDirection.IN.getCode());
                result.add(inCmd);
            }
        }
        return result;
    }

    /**
     * 由供应行与产品读模型构建 IF-TRC-14 写编排命令。
     *
     * @param line       已同意供应行
     * @param operatorId 操作人
     * @param product    药品读模型
     * @return 写编排命令
     */
    public RecordTraceFromStocktakeGainLossLineSpec buildLineCommand(
        SupplyRequestModel line,
        Long operatorId,
        ProductDefinitionDisplayDto product
    ) {
        RecordTraceFromStocktakeGainLossLineSpec cmd = new RecordTraceFromStocktakeGainLossLineSpec();
        cmd.setOperatorId(operatorId);
        cmd.setSupplyTypeCode(line.getTypeEnum());
        cmd.setBusNo(line.getBusNo());
        cmd.setItemTable(line.getItemTable());
        cmd.setItemId(line.getItemId());
        cmd.setLotNumber(line.getLotNumber());
        cmd.setScannedTraceNos(line.getTraceNo());
        cmd.setProfitLossSplitQuantity(line.getItemQuantity());
        cmd.setLocationId(line.getPurposeLocationId());
        cmd.setLocationTypeEnum(
            locationQueryService.requireSupplyRequestTypeForLocationId(line.getPurposeLocationId()).getCode());
        if (product != null) {
            cmd.setUnitConversionSource(ProductDefinitionDisplaySupport.toUnitConversionSource(product));
            if (StringUtils.isNotEmpty(product.getSplitUnitCode())) {
                cmd.setSplitUnitCode(product.getSplitUnitCode().trim());
            }
            // 盘盈新建主档：贴码单位取药典追溯单位（可游离包装单位），禁止再用包装单位顶替
            if (StringUtils.isNotEmpty(product.getTraceUnitCode())) {
                cmd.setTraceUnitCode(product.getTraceUnitCode().trim());
            }
        }
        if (trimToNull(cmd.getSplitUnitCode()) == null && line.getUnitCode() != null) {
            cmd.setSplitUnitCode(line.getUnitCode().trim());
        }
        return cmd;
    }

    /**
     * 组装单端供应写命令公共字段（不含 traceNos / 方向 / 数量）。
     *
     * @param cmd 盘点/损益写编排命令
     * @return 待填充 traceNos 的供应写命令
     */
    private RecordTraceFromSupplyLineSpec buildBaseSupplyCommand(RecordTraceFromStocktakeGainLossLineSpec cmd) {
        RecordTraceFromSupplyLineSpec supplyCmd = new RecordTraceFromSupplyLineSpec();
        supplyCmd.setOperatorId(cmd.getOperatorId());
        supplyCmd.setSupplyTypeCode(cmd.getSupplyTypeCode());
        supplyCmd.setBusNo(cmd.getBusNo());
        supplyCmd.setItemTable(TableNameConstants.MED_MEDICATION_DEFINITION);
        supplyCmd.setItemId(cmd.getItemId());
        supplyCmd.setLotNumber(cmd.getLotNumber());
        supplyCmd.setUnitCode(cmd.getSplitUnitCode());
        supplyCmd.setUnitConversionSource(cmd.getUnitConversionSource());
        supplyCmd.setLocationId(cmd.getLocationId());
        supplyCmd.setLocationTypeEnum(cmd.getLocationTypeEnum());
        return supplyCmd;
    }

    /**
     * 解析盘盈新建主档所需的追溯码单位。
     *
     * @param cmd 盘点/损益写编排命令
     * @return 追溯码单位 code
     * @throws BusinessException 无法解析时
     */
    private String resolveTraceUnitCode(RecordTraceFromStocktakeGainLossLineSpec cmd) {
        if (trimToNull(cmd.getTraceUnitCode()) != null) {
            return cmd.getTraceUnitCode().trim();
        }
        MedicationProductUnitConversionSource source = cmd.getUnitConversionSource();
        // 盘盈新建主档：优先药典追溯贴码单位，再回落包装/拆零
        if (source != null && trimToNull(source.getTraceUnitCode()) != null) {
            return source.getTraceUnitCode().trim();
        }
        if (source != null && trimToNull(source.getPackageUnitCode()) != null) {
            return source.getPackageUnitCode().trim();
        }
        if (trimToNull(cmd.getSplitUnitCode()) != null) {
            return cmd.getSplitUnitCode().trim();
        }
        throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "盘盈入库须指定追溯码单位");
    }

    /**
     * 校验盘亏 OUT 拆零总量不超过行盈亏量，防止扫码与账面严重偏离。
     *
     * @param outCommands            待 OUT 命令
     * @param absProfitLossSplitQty  行盈亏拆零量绝对值
     * @throws BusinessException 出库量超盈亏时
     */
    private void assertLossSplitQuantityWithinProfitLoss(
        List<RecordTraceFromSupplyLineSpec> outCommands,
        BigDecimal absProfitLossSplitQty
    ) {
        BigDecimal lossSplitSum = BigDecimal.ZERO;
        for (RecordTraceFromSupplyLineSpec outCmd : outCommands) {
            if (outCmd != null && outCmd.getQuantity() != null) {
                lossSplitSum = lossSplitSum.add(outCmd.getQuantity());
            }
        }
        if (lossSplitSum.compareTo(absProfitLossSplitQty) > 0) {
            throw new BusinessException(
                ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT,
                "盘亏/报损追溯出库量超过行盈亏量，请核对剩余扫码与账面");
        }
    }

    /**
     * 盘点/损益 agree 路径追溯写编排命令（AS-CUS-TRC-001 · IF-TRC-14）。
     */
    @Getter
    @Setter
    public static class RecordTraceFromStocktakeGainLossLineSpec {

        /** 操作人标识。 */
        private Long operatorId;

        /** 供应类型 code。 */
        private String supplyTypeCode;

        /** 业务单据号。 */
        private String busNo;

        /** 品项表。 */
        private String itemTable;

        /** 品项标识。 */
        private Long itemId;

        /** 批号。 */
        private String lotNumber;

        /** 扫到的追溯码逗号串（盘点=剩余码；损益=盈亏码本身）。 */
        private String scannedTraceNos;

        /** 行盈亏拆零量（负为盘亏/报损，正为盘盈/报溢）。 */
        private BigDecimal profitLossSplitQuantity;

        /** 拆零单位 code。 */
        private String splitUnitCode;

        /** 药品单位换算源。 */
        private MedicationProductUnitConversionSource unitConversionSource;

        /** 追溯码单位（盘盈入库建主档用）。 */
        private String traceUnitCode;

        /** 子表库位。 */
        private Long locationId;

        /** 库位类型。 */
        private String locationTypeEnum;
    }
}
