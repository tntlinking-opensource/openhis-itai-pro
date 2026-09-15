/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderexecute.mapper;

import com.openhis.health.application.shared.advice.OrderChargePricingAssembler;
import com.openhis.health.application.inpatient.nursestation.orderexecute.request.ListNurseStationOrderExecuteForPrintRequest;
import com.openhis.health.application.inpatient.nursestation.orderexecute.request.ListNurseStationOrderExecuteRequest;
import com.openhis.health.application.inpatient.nursestation.orderexecute.support.NurseStationOrderExecuteListQuery;
import com.openhis.health.application.inpatient.nursestation.orderexecute.support.NurseStationOrderExecuteListReadAssembler;
import com.openhis.health.application.inpatient.nursestation.orderexecute.support.NurseStationOrderExecuteListResult;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteRowDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteWriteSlotDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.response.ExecuteNurseStationOrdersResponse;
import com.openhis.health.application.inpatient.nursestation.orderexecute.response.ListNurseStationOrderExecuteResponse;
import com.openhis.health.application.inpatient.nursestation.orderexecute.response.SkipNurseStationOrderSlotsResponse;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.health.domain.clinical.procedure.command.CompleteProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.command.CreateProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.command.SkipProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.command.StartProcedureExecutionCommand;
import com.openhis.health.domain.medication.medicationdispense.command.GenerateDispenseFromExecutionCommand;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.application.shared.advice.InpatientOrderListSkeleton;
import com.openhis.health.domain.share.constants.ChargeItemDmConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.workflow.devicedispense.command.GenerateDeviceDispenseFromTemporaryPathCommand;
import com.openhis.health.domain.workflow.devicerequest.command.CreateExecutedDeviceOrderCommand;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * {@link NurseStationOrderExecuteMapper} 实现：Request/Command/Response 字段搬运与 Tab 过滤辅助。
 *
 * <p>禁止承载业务流程判断；Tab 匹配、长临解析等纯映射逻辑集中在本类，
 * 编排与 Gate 在 AppService / Assembler。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseStationOrderExecuteMapperImpl implements NurseStationOrderExecuteMapper {

    private final NurseStationOrderExecuteListReadAssembler readAssembler;

    @Override
    public NurseStationOrderExecuteListQuery toListQuery(ListNurseStationOrderExecuteRequest request) {
        if (request == null) {
            return new NurseStationOrderExecuteListQuery();
        }
        // EXE-01：不下发打印专用区间；planTimeFrom / 开嘱日区间保持空；列表页不挂打印 enrich
        return new NurseStationOrderExecuteListQuery()
            .setEncounterIds(request.getEncounterIds())
            .setExecuteTab(request.getExecuteTab())
            .setTherapyType(request.getTherapyType())
            .setExecutionDeadline(request.getExecutionDeadline())
            .setMedicineCategoryTab(request.getMedicineCategoryTab())
            .setIncludePrintDetails(Boolean.FALSE);
    }

    @Override
    public NurseStationOrderExecuteListQuery toListQueryForPrint(ListNurseStationOrderExecuteForPrintRequest request) {
        if (request == null) {
            return new NurseStationOrderExecuteListQuery();
        }
        // 打印中心：透传计划点下界与开嘱日闭区间；挂载套餐/部位标本供申请单打印
        return new NurseStationOrderExecuteListQuery()
            .setEncounterIds(request.getEncounterIds())
            .setExecuteTab(request.getExecuteTab())
            .setTherapyType(request.getTherapyType())
            .setExecutionDeadline(request.getExecutionDeadline())
            .setPlanTimeFrom(request.getPlanTimeFrom())
            .setReqOpenedTimeFrom(request.getReqOpenedTimeFrom())
            .setReqOpenedTimeTo(request.getReqOpenedTimeTo())
            .setMedicineCategoryTab(request.getMedicineCategoryTab())
            .setExecutionRecordHistory(request.getExecutionRecordHistory())
            .setIncludePrintDetails(Boolean.TRUE);
    }

    @Override
    public ListNurseStationOrderExecuteResponse toListResponse(NurseStationOrderExecuteListResult result) {
        ListNurseStationOrderExecuteResponse response = new ListNurseStationOrderExecuteResponse();
        if (result == null || result.getGroups() == null) {
            response.setGroups(Collections.emptyList());
            return response;
        }
        response.setGroups(result.getGroups());
        return response;
    }

    @Override
    public InpatientEncounterPageQuery toInpatientEncounterPageQuery(List<Long> encounterIds, List<Long> managedWardIds) {
        return readAssembler.toInpatientEncounterPageQuery(encounterIds, managedWardIds);
    }

    @Override
    public String resolveTherapyEnum(String therapyType) {
        return readAssembler.resolveTherapyEnum(therapyType);
    }

    @Override
    public void applyExecuteTabSlotFilter(
        NurseStationOrderExecuteRowDto row,
        String executeTab,
        LocalDateTime executionDeadline,
        LocalDateTime planTimeFrom) {
        readAssembler.applyExecuteTabSlotFilter(row, executeTab, executionDeadline, planTimeFrom);
    }

    /**
     * 批量执行成功响应（含软提示；无提示时 warnings 为空列表）。
     *
     * @param processedCount 成功槽位数
     * @param warnings       软提示文案（BR-IP-EXE-034）；可空
     * @return 响应 DTO
     */
    @Override
    public ExecuteNurseStationOrdersResponse toExecuteResponse(int processedCount, List<String> warnings) {
        ExecuteNurseStationOrdersResponse response = new ExecuteNurseStationOrdersResponse();
        response.setProcessedCount(processedCount);
        // 无软提示时回空列表，避免前端判 null
        if (warnings == null) {
            response.setWarnings(Collections.emptyList());
        } else {
            response.setWarnings(warnings);
        }
        return response;
    }

    /**
     * 批量不执行成功响应。
     *
     * @param processedCount 成功槽位数
     * @return 响应 DTO
     */
    @Override
    public SkipNurseStationOrderSlotsResponse toSkipResponse(int processedCount) {
        SkipNurseStationOrderSlotsResponse response = new SkipNurseStationOrderSlotsResponse();
        response.setProcessedCount(processedCount);
        return response;
    }

    /**
     * 写槽位 → 创建 Procedure 命令（BR-IP-EXE-015 skipDuplicate=true）。
     */
    @Override
    public CreateProcedureRecordCommand toCreateProcedureRecordCommand(
        NurseStationOrderExecuteWriteSlotDto item,
        Long requestId,
        String requestTable,
        Long encounterId,
        Long patientId,
        String categoryEnum,
        Long operatorId) {
        CreateProcedureRecordCommand command = new CreateProcedureRecordCommand();
        command.setOperatorId(operatorId);
        // 同一 request+planTime 重复提交时领域层幂等跳过（防 double-click）
        command.setSkipDuplicateRequestAnchorCheck(Boolean.TRUE);
        if (item == null) {
            return command;
        }
        command.setRequestId(requestId);
        command.setRequestTable(requestTable);
        command.setEncounterId(encounterId);
        command.setPatientId(patientId);
        command.setCategoryEnum(categoryEnum);
        // occurrence=护士选择的计划点；recorded 在 complete 阶段写入
        command.setOccurrenceTime(item.getPlanTime());
        return command;
    }

    /**
     * 启动 Procedure 执行。
     */
    @Override
    public StartProcedureExecutionCommand toStartProcedureExecutionCommand(Long procedureId, Long operatorId) {
        StartProcedureExecutionCommand command = new StartProcedureExecutionCommand();
        command.setProcedureId(procedureId);
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 完成 Procedure 执行（recorded=护士 actualTime；逻辑锚点仍为 create 时的 occurrence=planTime）。
     */
    @Override
    public CompleteProcedureRecordCommand toCompleteProcedureRecordCommand(
        Long procedureId, Long operatorId, LocalDateTime recordedTime) {
        CompleteProcedureRecordCommand command = new CompleteProcedureRecordCommand();
        command.setProcedureId(procedureId);
        command.setOperatorId(operatorId);
        // 护士站执行无结构化报告，固定结果文案
        command.setResultText(InpatientNurseStationConstants.INPATIENT_NURSE_EXECUTE_PROCEDURE_RESULT_TEXT);
        command.setReportJson(null);
        // 展示/事实时间：actualTime；发药 plannedDispenseTime 与账单 occurrence 仍用 planTime
        command.setRecordedTime(recordedTime);
        return command;
    }

    /**
     * 登记计划点不执行（BR-IP-EXE-021）。
     */
    @Override
    public SkipProcedureRecordCommand toSkipProcedureRecordCommand(
        NurseStationOrderExecuteWriteSlotDto item,
        Long requestId,
        String requestTable,
        Long encounterId,
        Long patientId,
        String categoryEnum,
        Long operatorId,
        LocalDateTime recordedTime) {
        SkipProcedureRecordCommand command = new SkipProcedureRecordCommand();
        command.setOperatorId(operatorId);
        command.setSkipDuplicateRequestAnchorCheck(Boolean.TRUE);
        if (item == null) {
            return command;
        }
        command.setRequestId(requestId);
        command.setRequestTable(requestTable);
        command.setEncounterId(encounterId);
        command.setPatientId(patientId);
        command.setCategoryEnum(categoryEnum);
        command.setOccurrenceTime(item.getPlanTime());
        command.setRecordedTime(recordedTime);
        return command;
    }

    /**
     * 长期药品执行 → 新建发放单命令。
     */
    @Override
    public GenerateDispenseFromExecutionCommand toGenerateDispenseFromExecutionCommand(
        Long medReqId,
        Long procedureId,
        LocalDateTime planTime,
        Long locationId,
        String lotNumber,
        BigDecimal quantity,
        String unitCode,
        Long medicationId,
        Long encounterId,
        Long patientId) {
        GenerateDispenseFromExecutionCommand command = new GenerateDispenseFromExecutionCommand();
        command.setMedReqId(medReqId);
        command.setProcedureId(procedureId);
        command.setPlannedDispenseTime(planTime);
        // 参数语义：频次计划槽位（汇总领药勾选时间点）；实际执行时分在 Procedure.recorded，不写本字段
        command.setLocationId(locationId);
        command.setLotNumber(lotNumber);
        command.setQuantity(quantity);
        command.setUnitCode(unitCode);
        command.setMedicationId(medicationId);
        command.setEncounterId(encounterId);
        command.setPatientId(patientId);
        return command;
    }

    /**
     * 药/诊嘱执行 → 新建待结算收费明细（generate_source=ORDER_EXECUTE）。
     */
    @Override
    public CreateChargeItemCommand toCreatePlannedChargeItemFromOrderExecutionCommand(
        InpatientOrderListSkeleton order,
        ChargeItem template,
        ChargeItemDefinition priceLine,
        Long accountId,
        Long procedureId,
        LocalDateTime planTime,
        BigDecimal quantity,
        String quantityUnitCode,
        ProductDefinitionDisplayDto medicationProduct,
        Long dispenseId,
        String dispenseTable,
        Long operatorId,
        Long orgId) {
        CreateChargeItemCommand command = new CreateChargeItemCommand();
        command.setOperatorId(operatorId);
        CreateChargeItemCommand.Item item = new CreateChargeItemCommand.Item();
        boolean medicationCharge = false;
        boolean serviceCharge = false;
        if (order != null) {
            item.setPatientId(order.getPatientId());
            item.setEncounterId(order.getEncounterId());
            if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
                // 药嘱：service=med_request，product=medication_definition
                medicationCharge = true;
                item.setServiceTable(TableNameConstants.MED_MEDICATION_REQUEST);
                item.setServiceId(order.getMedReqId());
                item.setProductTable(TableNameConstants.MED_MEDICATION_DEFINITION);
                item.setProductId(order.getMedicationId());
            } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
                serviceCharge = true;
                item.setServiceTable(TableNameConstants.WKF_SERVICE_REQUEST);
                item.setServiceId(order.getOrderId());
                item.setProductTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
                item.setProductId(order.getActivityId());
            }
        }
        boolean productPricedCharge = medicationCharge || serviceCharge;
        if (template != null) {
            // 复制费用模板非金额字段；药/诊金额须按执行 quantity/unit 与目录比例重算
            item.setDefinitionId(template.getDefinitionId());
            if (!productPricedCharge) {
                item.setUnitPrice(template.getUnitPrice());
                item.setQuantityUnit(template.getQuantityUnit());
                item.setBaseAmount(template.getBaseAmount());
                item.setDiscountAmount(template.getDiscountAmount());
                item.setSurchargeAmount(template.getSurchargeAmount());
            }
            if (StringUtils.isNotEmpty(template.getProductTable())) {
                item.setProductTable(template.getProductTable());
            }
            if (template.getProductId() != null) {
                item.setProductId(template.getProductId());
            }
            if (template.getOrgId() != null) {
                item.setOrgId(template.getOrgId());
            }
            if (template.getRequestingOrgId() != null) {
                item.setRequestingOrgId(template.getRequestingOrgId());
            }
            if (StringUtils.isNotEmpty(template.getContextEnum())) {
                item.setContextEnum(template.getContextEnum());
            }
        }
        if (item.getOrgId() == null) {
            item.setOrgId(orgId);
        }
        if (item.getRequestingOrgId() == null) {
            item.setRequestingOrgId(orgId);
        }
        item.setAccountId(accountId);
        BigDecimal qty = quantity;
        if (productPricedCharge) {
            // 药/诊：quantity/unit 与请求行一致，单价按目录单位槽对定价行比例换算
            OrderChargePricingAssembler.applyOrderChargePricing(
                item, medicationProduct, priceLine, qty, quantityUnitCode);
        } else {
            if (qty == null) {
                qty = BigDecimal.ONE;
            }
            if (item.getQuantityUnit() == null) {
                item.setQuantityUnit(ChargeItemDmConstants.DEFAULT_QUANTITY_UNIT);
            }
            item.setQuantityValue(qty);
            if (item.getDefinitionId() == null) {
                this.applySalePriceLine(item, priceLine, qty);
            }
            BigDecimal unitPrice = item.getUnitPrice();
            if (unitPrice == null) {
                unitPrice = BigDecimal.ZERO;
            }
            item.setTotalPrice(unitPrice.multiply(qty));
            if (item.getBaseAmount() == null) {
                item.setBaseAmount(item.getTotalPrice());
            }
            if (item.getDiscountAmount() == null) {
                item.setDiscountAmount(item.getTotalPrice());
            }
            if (item.getSurchargeAmount() == null) {
                item.setSurchargeAmount(BigDecimal.ZERO);
            }
        }
        if (planTime != null) {
            item.setOccurrenceTime(planTime);
        } else {
            item.setOccurrenceTime(LocalDateTime.now());
        }
        item.setProcedureId(procedureId);
        item.setDispenseId(dispenseId);
        item.setDispenseTable(dispenseTable);
        item.setGenerateSourceEnum(GenerateSource.ORDER_EXECUTE.getCode());
        if (item.getContextEnum() == null) {
            item.setContextEnum(this.resolveChargeContext(item.getServiceTable()));
        }
        command.setItems(List.of(item));
        return command;
    }

    /**
     * 绑定项执行 → 新建待结算收费明细（generate_source=ORDER_EXECUTE）。
     */
    @Override
    public CreateChargeItemCommand toCreatePlannedChargeItemFromBindExecutionCommand(
        Long patientId,
        Long encounterId,
        String serviceTable,
        Long serviceId,
        String productTable,
        Long productId,
        ChargeItemDefinition priceLine,
        Long accountId,
        Long procedureId,
        LocalDateTime planTime,
        BigDecimal quantity,
        String quantityUnitCode,
        ProductDefinitionDisplayDto productRow,
        Long dispenseId,
        String dispenseTable,
        Long operatorId,
        Long orgId) {
        CreateChargeItemCommand command = new CreateChargeItemCommand();
        command.setOperatorId(operatorId);
        CreateChargeItemCommand.Item item = new CreateChargeItemCommand.Item();
        item.setPatientId(patientId);
        item.setEncounterId(encounterId);
        item.setServiceTable(serviceTable);
        item.setServiceId(serviceId);
        item.setProductTable(productTable);
        item.setProductId(productId);
        item.setAccountId(accountId);
        item.setOrgId(orgId);
        item.setRequestingOrgId(orgId);
        OrderChargePricingAssembler.applyOrderChargePricing(
            item, productRow, priceLine, quantity, quantityUnitCode);
        if (planTime != null) {
            item.setOccurrenceTime(planTime);
        } else {
            item.setOccurrenceTime(LocalDateTime.now());
        }
        item.setProcedureId(procedureId);
        item.setDispenseId(dispenseId);
        item.setDispenseTable(dispenseTable);
        item.setGenerateSourceEnum(GenerateSource.ORDER_EXECUTE.getCode());
        item.setContextEnum(this.resolveChargeContext(serviceTable));
        command.setItems(List.of(item));
        return command;
    }

    /**
     * 绑定耗材执行 → 生成已完成 DeviceRequest 命令。
     */
    @Override
    public CreateExecutedDeviceOrderCommand toCreateExecutedDeviceOrderCommand(
        Long deviceDefId,
        BigDecimal quantity,
        String unitCode,
        Long performLocation,
        Long encounterId,
        Long patientId,
        String basedOnTable,
        Long basedOnId,
        Long packageId,
        Long operatorId) {
        CreateExecutedDeviceOrderCommand command = new CreateExecutedDeviceOrderCommand();
        command.setOperatorId(operatorId);
        CreateExecutedDeviceOrderCommand.CreateExecutedDeviceOrderItemCommand line =
            new CreateExecutedDeviceOrderCommand.CreateExecutedDeviceOrderItemCommand();
        line.setGenerateSourceEnum(GenerateSource.ORDER_EXECUTE.getCode());
        line.setDeviceDefId(deviceDefId);
        line.setQuantity(quantity);
        line.setUnitCode(unitCode);
        line.setPerformLocation(performLocation);
        line.setEncounterId(encounterId);
        line.setPatientId(patientId);
        line.setBasedOnTable(basedOnTable);
        line.setBasedOnId(basedOnId);
        // 与主药嘱组套对齐（成组输液绑耗材共用一份时取代表行 package_id）
        line.setPackageId(packageId);
        command.setItems(List.of(line));
        return command;
    }

    /**
     * 绑定耗材执行 → 临时路径草稿发放单命令。
     */
    @Override
    public GenerateDeviceDispenseFromTemporaryPathCommand toGenerateDeviceDispenseFromTemporaryPathCommand(
        Long deviceReqId,
        Long deviceDefId,
        Long encounterId,
        Long patientId,
        BigDecimal quantity,
        String unitCode,
        LocalDateTime limitTime,
        Long operatorId) {
        GenerateDeviceDispenseFromTemporaryPathCommand command = new GenerateDeviceDispenseFromTemporaryPathCommand();
        command.setOperatorId(operatorId);
        GenerateDeviceDispenseFromTemporaryPathCommand.GenerateDeviceDispenseFromTemporaryPathItemCommand line =
            new GenerateDeviceDispenseFromTemporaryPathCommand.GenerateDeviceDispenseFromTemporaryPathItemCommand();
        line.setDeviceReqId(deviceReqId);
        line.setDeviceDefId(deviceDefId);
        line.setEncounterId(encounterId);
        line.setPatientId(patientId);
        line.setQuantity(quantity);
        line.setUnitCode(unitCode);
        line.setLimitTime(limitTime);
        command.setItems(List.of(line));
        return command;
    }

    /**
     * 将销售定价行写入收费命令项（definitionId、单价与金额字段）。
     *
     * @param item      命令项
     * @param priceLine 销售定价行
     * @param quantity  计价数量
     * @throws BusinessException 定价行为空
     */
    private void applySalePriceLine(CreateChargeItemCommand.Item item, ChargeItemDefinition priceLine, BigDecimal quantity) {
        if (priceLine == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CHARGE_PRICE_LINE_REQUIRED);
        }
        item.setDefinitionId(priceLine.getId());
        BigDecimal unitPrice = priceLine.getPriceValue();
        if (unitPrice == null) {
            unitPrice = BigDecimal.ZERO;
        }
        BigDecimal qty = quantity;
        if (qty == null) {
            qty = BigDecimal.ONE;
        }
        BigDecimal totalPrice = unitPrice.multiply(qty);
        item.setUnitPrice(unitPrice);
        item.setTotalPrice(totalPrice);
        item.setBaseAmount(totalPrice);
        item.setDiscountAmount(totalPrice);
        item.setSurchargeAmount(BigDecimal.ZERO);
    }

    /**
     * 按 serviceTable 解析收费 context。
     *
     * @param serviceTable 上游表名
     * @return context code
     */
    private String resolveChargeContext(String serviceTable) {
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(serviceTable)
            || TableNameConstants.MED_MEDICATION_DISPENSE.equals(serviceTable)) {
            return ChargeItemDmConstants.CONTEXT_MEDICATION;
        }
        if (TableNameConstants.WKF_DEVICE_REQUEST.equals(serviceTable)
            || TableNameConstants.WKF_DEVICE_DISPENSE.equals(serviceTable)) {
            return ChargeItemDmConstants.CONTEXT_DEVICE;
        }
        return ChargeItemDmConstants.CONTEXT_ACTIVITY;
    }
}
