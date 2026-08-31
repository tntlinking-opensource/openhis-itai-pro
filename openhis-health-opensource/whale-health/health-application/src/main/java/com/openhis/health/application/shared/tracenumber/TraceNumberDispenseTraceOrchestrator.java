/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.tracenumber;

import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.custom.tracenumber.queries.model.TraceNumberRecordQueryModel;
import com.openhis.health.domain.custom.tracenumber.service.TraceNumberQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.enums.TraceRecordCategory;
import com.openhis.health.domain.share.enums.TraceNumberRecordOperationType;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.application.shared.tracenumber.TraceNumberRecordOrchestrator.ExplicitPerCodeSplit;
import com.openhis.health.application.shared.tracenumber.TraceNumberRecordOrchestrator.RecordTraceFromDispenseLineSpec;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 发药/退药追溯写编排 Support（AS-CUS-TRC-001 · IF-TRC-16/17；DD 追溯码可选扫 §3/§5）。
 *
 * <p>供门诊/住院发药与退药 AppService 在核发/确认事务内调用；不开新事务。
 * 消耗侧扫码可选：盖不住或未扫则跳过写主子表，不阻断发药主流程。</p>
 */
@Component
@RequiredArgsConstructor
public class TraceNumberDispenseTraceOrchestrator {

    /** 追溯写侧编排（含逗号串展开）。 */
    private final TraceNumberRecordOrchestrator traceNumberRecordOrchestrator;

    /** 库位类型解析。 */
    private final LocationQueryService locationQueryService;

    /** 追溯码读侧（覆盖判定、剩余先出、OUT 流水镜像）。 */
    private final TraceNumberQueryService traceNumberQueryService;

    /**
     * 批量记发药 OUT 流水（IF-TRC-16 · DD §3）。
     *
     * <p>有码且剩余合计盖住本次拆零 → 剩余先出写每码流水；否则跳过写追溯（等同未扫），不抛。</p>
     *
     * @param dispenses              已核发发放行
     * @param operatorId             操作人
     * @param productByMedicationId  药品投影（可为空 map，缺失时跳过 quantity 换算校验由领域 Gate）
     */
    public void recordDispenseOutLines(
        List<MedicationDispense> dispenses,
        Long operatorId,
        java.util.Map<Long, ProductDefinitionDisplayDto> productByMedicationId
    ) {
        // 无核发行：无需写发药 OUT
        if (dispenses == null || dispenses.isEmpty()) {
            return;
        }
        java.util.Map<Long, ProductDefinitionDisplayDto> safeProductMap = productByMedicationId;
        // 药品投影未传入：按空 map 处理，缺失投影时后续覆盖校验走领域默认
        if (safeProductMap == null) {
            safeProductMap = java.util.Map.of();
        }
        for (MedicationDispense dispense : dispenses) {
            // 空行或未扫码：本行不写 OUT
            if (dispense == null || StringUtils.isEmpty(dispense.getTraceNo())) {
                continue;
            }
            // 无码批：仅业务行已存 trace_no，不写主表/流水（BR-TRC-LOT-SOFT · Option A）
            if (!traceNumberQueryService.existsMasterByItemLot(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                dispense.getMedicationId(),
                dispense.getLotNumber())) {
                continue;
            }
            ProductDefinitionDisplayDto product = safeProductMap.get(dispense.getMedicationId());
            // 盖不住：跳过写 OUT，禁止带着不足码串扣主档（DD §3.1）
            if (!this.isDispenseOutCoverageSufficient(dispense, product)) {
                continue;
            }
            RecordTraceFromDispenseLineSpec cmd = this.buildDispenseLineCommand(
                dispense,
                operatorId,
                TraceNumberRecordOperationType.MEDICATION_DISPENSE_OUT.getCode(),
                TraceDirection.OUT.getCode(),
                product
            );
            traceNumberRecordOrchestrator.recordFromDispenseLine(cmd);
        }
    }

    /**
     * 批量记退药 IN 流水（IF-TRC-17 · DD §5）：按原发药 OUT 每码实扣镜像加回，禁止按当前剩余重算。
     *
     * <p>定位 OUT：先按退药行 {@code bus_no}；若无流水（对冲行新单号），再按行上已拷贝
     * {@code trace_no} 回落未回冲原发 OUT。原发未扫（无 OUT）→ 不写追溯。整单退全额镜像；
     * 部分退按 OUT 落库逆序耗尽。写 IN 时 {@code bus_no} 仍用退药行单号（码上放心 116）。</p>
     *
     * @param dispenses  已确认退药发放行（含原 trace_no / 退药 bus_no）
     * @param operatorId 操作人
     * @param productByMedicationId 药品投影
     */
    public void recordReturnInLines(
        List<MedicationDispense> dispenses,
        Long operatorId,
        java.util.Map<Long, ProductDefinitionDisplayDto> productByMedicationId
    ) {
        // 无退药行：无需写退药 IN
        if (dispenses == null || dispenses.isEmpty()) {
            return;
        }
        java.util.Map<Long, ProductDefinitionDisplayDto> safeProductMap = productByMedicationId;
        // 药品投影未传入：按空 map 处理，镜像换算缺失时走 OUT 合计回落
        if (safeProductMap == null) {
            safeProductMap = java.util.Map.of();
        }
        for (MedicationDispense dispense : dispenses) {
            // 空行：跳过本行退药 IN
            if (dispense == null) {
                continue;
            }
            // 无码批退药：业务行有码但无主档，跳过流水（与发药 OUT Option A 对称）
            if (!traceNumberQueryService.existsMasterByItemLot(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                dispense.getMedicationId(),
                dispense.getLotNumber())) {
                continue;
            }
            ProductDefinitionDisplayDto product = safeProductMap.get(dispense.getMedicationId());
            List<ExplicitPerCodeSplit> mirrored = this.buildReturnMirrorSplits(dispense, product);
            // 原发未扫或无 OUT 流水：不写 IN
            if (mirrored.isEmpty()) {
                continue;
            }
            RecordTraceFromDispenseLineSpec cmd = this.buildDispenseLineCommand(
                dispense,
                operatorId,
                TraceNumberRecordOperationType.MEDICATION_RETURN_IN.getCode(),
                TraceDirection.IN.getCode(),
                product
            );
            cmd.setExplicitPerCodeSplits(mirrored);
            // 镜像路径以显式每码为准；traceNos 仅作可读冗余
            StringBuilder joined = new StringBuilder();
            for (int i = 0; i < mirrored.size(); i++) {
                if (i > 0) {
                    joined.append(',');
                }
                joined.append(mirrored.get(i).getTraceNo());
            }
            cmd.setTraceNos(joined.toString());
            traceNumberRecordOrchestrator.recordFromDispenseLine(cmd);
        }
    }

    /**
     * 汇总核发记 OUT（IF-TRC-15）：锚定汇总单号，一行一条供应明细。
     *
     * @param itemTable        品项表
     * @param itemId           药品 id
     * @param locationId       核发药房
     * @param lotNumber        批号（可空）
     * @param traceNos         逗号串
     * @param unitCode         业务单位
     * @param quantity         业务数量
     * @param unitConversionSource 换算源
     * @param summaryNo        汇总单号
     * @param operatorId       操作人
     */
    public void recordSummaryDispenseOutLine(
        String itemTable,
        Long itemId,
        Long locationId,
        String lotNumber,
        String traceNos,
        String unitCode,
        BigDecimal quantity,
        MedicationProductUnitConversionSource unitConversionSource,
        String summaryNo,
        Long operatorId
    ) {
        if (StringUtils.isEmpty(traceNos) || locationId == null) {
            return;
        }
        // 无码批汇总核发：仅 SupplyDelivery 业务码，不写主表流水
        if (!traceNumberQueryService.existsMasterByItemLot(itemTable, itemId, lotNumber)) {
            return;
        }
        List<String> codes = traceNumberRecordOrchestrator.splitTraceNos(traceNos);
        // 盖不住：跳过写 OUT，不挡汇总核发主流程
        if (!traceNumberQueryService.isRemainingSplitSufficientForDispenseOut(
            codes, quantity, unitCode, unitConversionSource)) {
            return;
        }
        RecordTraceFromDispenseLineSpec cmd = new RecordTraceFromDispenseLineSpec();
        cmd.setOperatorId(operatorId);
        cmd.setOperationType(TraceNumberRecordOperationType.MEDICATION_DISPENSE_OUT.getCode());
        cmd.setRecordCategoryEnum(TraceRecordCategory.HOSPITAL_IO.getCode());
        cmd.setRecordDirectionEnum(TraceDirection.OUT.getCode());
        cmd.setBusNo(summaryNo);
        cmd.setItemTable(itemTable);
        cmd.setItemId(itemId);
        cmd.setLotNumber(lotNumber);
        cmd.setTraceNos(traceNos);
        cmd.setUnitCode(unitCode);
        cmd.setQuantity(quantity);
        cmd.setUnitConversionSource(unitConversionSource);
        cmd.setLocationId(locationId);
        cmd.setLocationTypeEnum(
            locationQueryService.requireSupplyRequestTypeForLocationId(locationId).getCode());
        traceNumberRecordOrchestrator.recordFromDispenseLine(cmd);
    }

    /**
     * 发药 OUT 覆盖是否充足（剩余合计盖住本次拆零）；不足则 false，不抛。
     *
     * @param dispense 发放行
     * @param product  药品投影
     * @return 可写 OUT 为 true
     */
    private boolean isDispenseOutCoverageSufficient(
        MedicationDispense dispense,
        ProductDefinitionDisplayDto product
    ) {
        // 空码行不写 OUT
        if (dispense == null || StringUtils.isEmpty(dispense.getTraceNo())) {
            return false;
        }
        BigDecimal businessQty = dispense.getQuantity();
        // 行数量优先 quantity，空则回退 dispenseQuantity
        if (businessQty == null) {
            businessQty = dispense.getDispenseQuantity();
        }
        MedicationProductUnitConversionSource conversionSource = null;
        // 有药品投影时装配换算源
        if (product != null) {
            conversionSource = ProductDefinitionDisplaySupport.toUnitConversionSource(product);
        }
        List<String> codes = traceNumberRecordOrchestrator.splitTraceNos(dispense.getTraceNo());
        return traceNumberQueryService.isRemainingSplitSufficientForDispenseOut(
            codes,
            businessQty,
            dispense.getUnitCode(),
            conversionSource
        );
    }

    /**
     * 按原发药 OUT 流水构建退药 IN 每码加回量（DD §5.3）。
     *
     * <p>优先按退药行 {@code bus_no} 取 OUT；若为空（对冲行新单号与原发 MDP 不一致），
     * 再按行上已拷贝的 {@code trace_no} 定位尚未回冲的原发 OUT（只读追溯子表）。</p>
     *
     * @param dispense 退药确认后的发放行
     * @param product  药品投影
     * @return 镜像每码拆零；无 OUT 或退量为 0 时为空列表
     */
    private List<ExplicitPerCodeSplit> buildReturnMirrorSplits(
        MedicationDispense dispense,
        ProductDefinitionDisplayDto product
    ) {
        List<TraceNumberRecordQueryModel> outRecords =
            this.resolveDispenseOutRecordsForReturnMirror(dispense);
        // 原发未扫或无法按码回落：无 OUT 流水
        if (outRecords.isEmpty()) {
            return new ArrayList<>();
        }
        BigDecimal returnBusinessQty = dispense.getDispenseQuantity();
        // 退量优先 dispenseQuantity（退药域已写入），空则回退 quantity
        if (returnBusinessQty == null) {
            returnBusinessQty = dispense.getQuantity();
        }
        // 退量为空或非正：无可加回量
        if (returnBusinessQty == null || returnBusinessQty.compareTo(BigDecimal.ZERO) <= 0) {
            return new ArrayList<>();
        }
        MedicationProductUnitConversionSource conversionSource = null;
        // 有药品投影时装配换算源，供退量落到拆零
        if (product != null) {
            conversionSource = ProductDefinitionDisplaySupport.toUnitConversionSource(product);
        }
        BigDecimal returnSplit;
        // 有换算源：把退药业务量落到拆零；无则尝试直接用 OUT 总量（整单退场景）
        if (conversionSource != null && StringUtils.isNotEmpty(dispense.getUnitCode())) {
            returnSplit = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                conversionSource,
                dispense.getUnitCode(),
                returnBusinessQty
            );
        } else {
            returnSplit = this.sumOutSplitQuantities(outRecords);
        }
        BigDecimal outTotal = this.sumOutSplitQuantities(outRecords);
        // 整单退（退量 ≥ OUT 合计）：每码全额镜像
        if (returnSplit.compareTo(outTotal) >= 0) {
            List<ExplicitPerCodeSplit> full = new ArrayList<>();
            for (TraceNumberRecordQueryModel out : outRecords) {
                ExplicitPerCodeSplit row = new ExplicitPerCodeSplit();
                row.setTraceNo(out.getTraceNo());
                row.setSplitQuantity(out.getSplitQuantity());
                full.add(row);
            }
            return full;
        }
        // 部分退：按 OUT 落库逆序耗尽（后扣的先退回）
        List<TraceNumberRecordQueryModel> reverse = new ArrayList<>(outRecords);
        Collections.reverse(reverse);
        BigDecimal remain = returnSplit;
        List<ExplicitPerCodeSplit> partial = new ArrayList<>();
        for (TraceNumberRecordQueryModel out : reverse) {
            // 退量已耗尽：停止逆序加回
            if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal outSplit = out.getSplitQuantity();
            // OUT 行拆零为空或非正：不参与镜像
            if (outSplit == null || outSplit.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal take = outSplit;
            // 本码 OUT 超过剩余退量：只加回剩余部分
            if (take.compareTo(remain) > 0) {
                take = remain;
            }
            ExplicitPerCodeSplit row = new ExplicitPerCodeSplit();
            row.setTraceNo(out.getTraceNo());
            row.setSplitQuantity(take);
            partial.add(row);
            remain = remain.subtract(take);
        }
        return partial;
    }

    /**
     * 解析退药镜像所需的原发药 HOSPITAL_IO OUT 流水。
     *
     * <p>先按退药行 {@code bus_no} 查；为空时按已拷贝 {@code trace_no} 回落未回冲原发 OUT
     *（对冲行新单号场景，BR-PHA-038 / BR-IPR-007）。</p>
     *
     * @param dispense 退药发放行
     * @return OUT 读模型；无则空列表
     */
    private List<TraceNumberRecordQueryModel> resolveDispenseOutRecordsForReturnMirror(
        MedicationDispense dispense
    ) {
        // 空行：无镜像源
        if (dispense == null) {
            return new ArrayList<>();
        }
        List<TraceNumberRecordQueryModel> byBusNo =
            traceNumberQueryService.listDispenseHospitalIoOutRecordsByBusNo(dispense.getBusNo());
        // 退药行 bus_no 即原发单号（极少见同号）或历史双写：直接用
        if (!byBusNo.isEmpty()) {
            return byBusNo;
        }
        // 对冲行新 bus_no：按拷贝码定位尚未回冲的原发 OUT
        List<String> codes = traceNumberRecordOrchestrator.splitTraceNos(dispense.getTraceNo());
        // 行上无码：等同原发未扫，不写 IN
        if (codes.isEmpty()) {
            return new ArrayList<>();
        }
        return traceNumberQueryService.listUnreturnedDispenseHospitalIoOutRecordsByTraceNos(codes);
    }

    /**
     * 汇总 OUT 流水拆零量。
     *
     * @param outRecords OUT 读模型
     * @return 合计；空为 0
     */
    private BigDecimal sumOutSplitQuantities(List<TraceNumberRecordQueryModel> outRecords) {
        BigDecimal sum = BigDecimal.ZERO;
        for (TraceNumberRecordQueryModel out : outRecords) {
            // 空行或拆零缺失：不计入 OUT 合计
            if (out == null || out.getSplitQuantity() == null) {
                continue;
            }
            sum = sum.add(out.getSplitQuantity());
        }
        return sum;
    }

    /**
     * 构建发药/退药写命令。
     *
     * <p>追溯单位优先取首扫码主档 {@code traceUnitCode}，避免一律用目录包装单位覆盖已贴箱/盒码。</p>
     *
     * @param dispense      发放行
     * @param operatorId    操作人
     * @param operationType 操作类型
     * @param flowDirection 方向
     * @param product       药品投影
     * @return 写编排命令
     */
    private RecordTraceFromDispenseLineSpec buildDispenseLineCommand(
        MedicationDispense dispense,
        Long operatorId,
        String operationType,
        String flowDirection,
        ProductDefinitionDisplayDto product
    ) {
        RecordTraceFromDispenseLineSpec cmd = new RecordTraceFromDispenseLineSpec();
        cmd.setOperatorId(operatorId);
        cmd.setOperationType(operationType);
        cmd.setRecordCategoryEnum(TraceRecordCategory.HOSPITAL_IO.getCode());
        cmd.setRecordDirectionEnum(flowDirection);
        cmd.setBusNo(dispense.getBusNo());
        cmd.setItemTable(TableNameConstants.MED_MEDICATION_DEFINITION);
        cmd.setItemId(dispense.getMedicationId());
        cmd.setLotNumber(dispense.getLotNumber());
        cmd.setTraceNos(dispense.getTraceNo());
        cmd.setUnitCode(dispense.getUnitCode());
        BigDecimal businessQty = dispense.getQuantity();
        // 行数量优先 quantity，空则回退 dispenseQuantity
        if (businessQty == null) {
            businessQty = dispense.getDispenseQuantity();
        }
        cmd.setQuantity(businessQty);
        // 追溯单位+比例：首扫码主档优先（比例快照保证药典变更不漂移）
        List<String> codes = traceNumberRecordOrchestrator.splitTraceNos(dispense.getTraceNo());
        String resolvedTraceUnit = null;
        BigDecimal resolvedTraceRatio = null;
        // 有扫码时查首码主档单位与快照比
        if (!codes.isEmpty()) {
            Optional<String> fromMaster = traceNumberQueryService.findTraceUnitCodeByTraceNo(codes.get(0));
            if (fromMaster.isPresent()) {
                resolvedTraceUnit = fromMaster.get();
            }
            Optional<BigDecimal> ratioFromMaster =
                traceNumberQueryService.findTraceUnitRatioByTraceNo(codes.get(0));
            if (ratioFromMaster.isPresent()) {
                resolvedTraceRatio = ratioFromMaster.get();
            }
        }
        // 主档命中则写入；否则回退药典追溯贴码单位（禁止再用销售包装顶替）
        if (resolvedTraceUnit != null) {
            cmd.setTraceUnitCode(resolvedTraceUnit);
        } else if (product != null && StringUtils.isNotEmpty(product.getTraceUnitCode())) {
            cmd.setTraceUnitCode(product.getTraceUnitCode().trim());
        }
        // 换算源：有药品投影时装配，并用主档快照覆盖追溯槽
        if (product != null) {
            MedicationProductUnitConversionSource conversionSource =
                ProductDefinitionDisplaySupport.toUnitConversionSource(product);
            if (resolvedTraceUnit != null && resolvedTraceRatio != null) {
                conversionSource.setTraceUnitCode(resolvedTraceUnit);
                conversionSource.setTraceUnitRatio(resolvedTraceRatio);
            }
            cmd.setUnitConversionSource(conversionSource);
        }
        cmd.setLocationId(dispense.getLocationId());
        cmd.setLocationTypeEnum(
            locationQueryService.requireSupplyRequestTypeForLocationId(dispense.getLocationId()).getCode());
        return cmd;
    }
}
