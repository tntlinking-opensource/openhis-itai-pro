/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.tracenumber;

import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.custom.tracenumber.service.TraceNumberQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.enums.TraceRecordCategory;
import com.openhis.health.domain.share.enums.TraceNumberRecordOperationType;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.application.shared.tracenumber.TraceNumberRecordOrchestrator.RecordTraceFromDispenseLineSpec;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 发药/退药追溯写编排 Support（AS-CUS-TRC-001 · IF-TRC-16/17）。
 *
 * <p>供门诊/住院发药与退药 AppService 在核发/确认事务内调用；不开新事务。</p>
 */
@Component
@RequiredArgsConstructor
public class TraceNumberDispenseTraceOrchestrator {

    /** 追溯写侧编排（含逗号串展开）。 */
    private final TraceNumberRecordOrchestrator traceNumberRecordOrchestrator;

    /** 库位类型解析。 */
    private final LocationQueryService locationQueryService;

    /** 追溯码读侧（发药 OUT 剩余量 Gate）。 */
    private final TraceNumberQueryService traceNumberQueryService;

    /**
     * 批量记发药 OUT 流水（IF-TRC-16）。
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
        if (dispenses == null || dispenses.isEmpty()) {
            return;
        }
        java.util.Map<Long, ProductDefinitionDisplayDto> safeProductMap = productByMedicationId;
        if (safeProductMap == null) {
            safeProductMap = java.util.Map.of();
        }
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || StringUtils.isEmpty(dispense.getTraceNo())) {
                continue;
            }
            ProductDefinitionDisplayDto product = safeProductMap.get(dispense.getMedicationId());
            this.assertDispenseOutRemainingSufficient(dispense, product);
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
     * 批量记退药 IN 流水（IF-TRC-17）。
     *
     * @param dispenses  已确认退药发放行（含原 trace_no）
     * @param operatorId 操作人
     * @param productByMedicationId 药品投影
     */
    public void recordReturnInLines(
        List<MedicationDispense> dispenses,
        Long operatorId,
        java.util.Map<Long, ProductDefinitionDisplayDto> productByMedicationId
    ) {
        if (dispenses == null || dispenses.isEmpty()) {
            return;
        }
        java.util.Map<Long, ProductDefinitionDisplayDto> safeProductMap = productByMedicationId;
        if (safeProductMap == null) {
            safeProductMap = java.util.Map.of();
        }
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || StringUtils.isEmpty(dispense.getTraceNo())) {
                continue;
            }
            ProductDefinitionDisplayDto product = safeProductMap.get(dispense.getMedicationId());
            RecordTraceFromDispenseLineSpec cmd = this.buildDispenseLineCommand(
                dispense,
                operatorId,
                TraceNumberRecordOperationType.MEDICATION_RETURN_IN.getCode(),
                TraceDirection.IN.getCode(),
                product
            );
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
     * 发药 OUT 前校验主档剩余拆零量（与多码均分扣减一致，失败时给出可操作建议）。
     *
     * @param dispense 发放行
     * @param product  药品投影
     */
    private void assertDispenseOutRemainingSufficient(
        MedicationDispense dispense,
        ProductDefinitionDisplayDto product
    ) {
        if (dispense == null || StringUtils.isEmpty(dispense.getTraceNo())) {
            return;
        }
        BigDecimal businessQty = dispense.getQuantity();
        if (businessQty == null) {
            businessQty = dispense.getDispenseQuantity();
        }
        MedicationProductUnitConversionSource conversionSource = null;
        if (product != null) {
            conversionSource = ProductDefinitionDisplaySupport.toUnitConversionSource(product);
        }
        List<String> codes = traceNumberRecordOrchestrator.splitTraceNos(dispense.getTraceNo());
        traceNumberQueryService.assertRemainingSplitSufficientForDispenseOut(
            codes,
            businessQty,
            dispense.getUnitCode(),
            conversionSource
        );
    }

    /**
     * 构建发药/退药写命令。
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
        if (businessQty == null) {
            businessQty = dispense.getDispenseQuantity();
        }
        cmd.setQuantity(businessQty);
        if (product != null) {
            cmd.setUnitConversionSource(ProductDefinitionDisplaySupport.toUnitConversionSource(product));
            if (StringUtils.isNotEmpty(product.getPackageUnitCode())) {
                cmd.setTraceUnitCode(product.getPackageUnitCode().trim());
            }
        }
        cmd.setLocationId(dispense.getLocationId());
        cmd.setLocationTypeEnum(
            locationQueryService.requireSupplyRequestTypeForLocationId(dispense.getLocationId()).getCode());
        return cmd;
    }
}
