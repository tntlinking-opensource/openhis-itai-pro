/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.nursestation.orderexecute.mapper;

import com.openhis.health.application.common.assembler.advice.ProductDefinitionOrderChargePricingSupport;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecutePatientGroupDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteRowDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteSlotDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteWriteSlotDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.response.ExecuteNurseStationOrdersResponse;
import com.openhis.health.application.inpatient.nursestation.orderexecute.response.ListNurseStationOrderExecuteResponse;
import com.openhis.health.application.inpatient.nursestation.orderexecute.response.SkipNurseStationOrderSlotsResponse;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.chargeitem.command.ActivateChargeByExeCommand;
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
import com.openhis.health.domain.medication.medicationdispense.command.LinkDispenseProcedureCommand;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.application.querysupport.order.dto.InpatientOrderListSkeleton;
import com.openhis.health.domain.share.constants.ChargeItemDmConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.workflow.devicedispense.command.GenerateDeviceDispenseFromTemporaryPathCommand;
import com.openhis.health.domain.workflow.devicedispense.command.GenerateIssuedDeviceDispenseFromExecutionCommand;
import com.openhis.health.domain.workflow.devicerequest.command.CreateExecutedDeviceOrderCommand;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link NurseStationOrderExecuteMapper} 实现：Request/Command/Response 字段搬运与 Tab 过滤辅助。
 *
 * <p>禁止承载业务流程判断；Tab 匹配、长临解析等纯映射逻辑集中在本类，
 * 编排与 Gate 在 AppService / Assembler。</p>
 */
@Component
public class NurseStationOrderExecuteMapperImpl implements NurseStationOrderExecuteMapper {

    /**
     * 可见性查询入参：多选就诊 + 管理病区收窄（BR-IP-EXE-001 / BR-IP-NUR-003）。
     *
     * @param encounterIds   请求就诊主键列表；调用方保证非空
     * @param managedWardIds 登录者管理病区；可空
     * @return 住院信息读模型查询入参
     */
    @Override
    public InpatientEncounterPageQuery toInpatientEncounterPageQuery(List<Long> encounterIds, List<Long> managedWardIds) {
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        // 护士站可见性谓词：状态并集 + 管理病区 + encounterIdsIn 收窄（BR-IP-NUR-003 / BR-IP-EXE-001）。
        query.setNurseStationQueue(Boolean.TRUE);
        if (encounterIds != null && !encounterIds.isEmpty()) {
            query.setEncounterIdsIn(encounterIds);
        }
        if (managedWardIds != null && !managedWardIds.isEmpty()) {
            query.setManagedWardIds(managedWardIds);
        }
        return query;
    }

    /**
     * therapyType → TherapyType code；all 返回 null。
     *
     * @param therapyType all / long / temp；可空视为 all
     * @return 长临 code；all 时为 null
     */
    @Override
    public String resolveTherapyEnum(String therapyType) {
        String normalized = StringUtils.trimToEmpty(therapyType);
        if (StringUtils.isBlank(normalized)
            || InpatientNurseStationConstants.THERAPY_FILTER_ALL.equals(normalized)) {
            // all：读模型查询不过滤长临
            return null;
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_LONG.equals(normalized)) {
            return TherapyType.LONG_TERM.getCode();
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_TEMP.equals(normalized)) {
            return TherapyType.TEMPORARY.getCode();
        }
        return null;
    }

    /**
     * 按 executeTab 收窄槽位：医嘱展示字段不变，仅当前 Tab 对应类别保留 planTime ≤ deadline 的计划点（BR-IP-EXE-010）。
     *
     * @param row               已装配四类槽位的医嘱行；原地改写槽位字段
     * @param executeTab        pending / executed / skipped / cancelled
     * @param executionDeadline 截止时间
     */
    @Override
    public void applyExecuteTabSlotFilter(
        NurseStationOrderExecuteRowDto row,
        String executeTab,
        LocalDateTime executionDeadline) {
        if (row == null || executionDeadline == null) {
            return;
        }
        String tab = StringUtils.trimToEmpty(executeTab);
        List<NurseStationOrderExecuteSlotDto> pendingSource = row.getPendingSlots();
        List<NurseStationOrderExecuteSlotDto> executedSource = row.getExecutedSlots();
        List<NurseStationOrderExecuteSlotDto> skippedSource = row.getSkippedSlots();
        List<NurseStationOrderExecuteSlotDto> cancelledSource = row.getCancelledSlots();
        // 四类槽位字段同构保留，非当前 Tab 一律清空，避免前端误读其他状态计划点
        row.setPendingSlots(Collections.emptyList());
        row.setExecutedSlots(Collections.emptyList());
        row.setSkippedSlots(Collections.emptyList());
        row.setCancelledSlots(Collections.emptyList());
        if (InpatientNurseStationConstants.EXECUTE_TAB_PENDING.equals(tab)) {
            row.setPendingSlots(this.filterSlotsWithinDeadline(pendingSource, executionDeadline));
            return;
        }
        if (InpatientNurseStationConstants.EXECUTE_TAB_EXECUTED.equals(tab)) {
            row.setExecutedSlots(this.filterSlotsWithinDeadline(executedSource, executionDeadline));
            return;
        }
        if (InpatientNurseStationConstants.EXECUTE_TAB_SKIPPED.equals(tab)) {
            row.setSkippedSlots(this.filterSlotsWithinDeadline(skippedSource, executionDeadline));
            return;
        }
        if (InpatientNurseStationConstants.EXECUTE_TAB_CANCELLED.equals(tab)) {
            row.setCancelledSlots(this.filterSlotsWithinDeadline(cancelledSource, executionDeadline));
        }
    }

    /**
     * 分组列表 → 列表响应。
     *
     * @param groups 患者分组；可 null
     * @return 列表响应
     */
    @Override
    public ListNurseStationOrderExecuteResponse toListResponse(List<NurseStationOrderExecutePatientGroupDto> groups) {
        ListNurseStationOrderExecuteResponse response = new ListNurseStationOrderExecuteResponse();
        if (groups == null) {
            response.setGroups(Collections.emptyList());
            return response;
        }
        response.setGroups(groups);
        return response;
    }

    /**
     * 批量执行成功响应。
     *
     * @param processedCount 成功槽位数
     * @return 响应 DTO
     */
    @Override
    public ExecuteNurseStationOrdersResponse toExecuteResponse(int processedCount) {
        ExecuteNurseStationOrdersResponse response = new ExecuteNurseStationOrdersResponse();
        response.setProcessedCount(processedCount);
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
     * 完成 Procedure 执行。
     */
    @Override
    public CompleteProcedureRecordCommand toCompleteProcedureRecordCommand(Long procedureId, Long operatorId) {
        CompleteProcedureRecordCommand command = new CompleteProcedureRecordCommand();
        command.setProcedureId(procedureId);
        command.setOperatorId(operatorId);
        // 护士站执行无结构化报告，固定结果文案
        command.setResultText(InpatientNurseStationConstants.INPATIENT_NURSE_EXECUTE_PROCEDURE_RESULT_TEXT);
        command.setReportJson(null);
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
     * 临时药品执行 → 挂接已有发放单命令。
     */
    @Override
    public LinkDispenseProcedureCommand toLinkDispenseProcedureCommand(
        Long dispenseId,
        Long procedureId,
        LocalDateTime plannedDispenseTime,
        Long operatorId) {
        LinkDispenseProcedureCommand command = new LinkDispenseProcedureCommand();
        command.setDispenseId(dispenseId);
        command.setProcedureId(procedureId);
        command.setPlannedDispenseTime(plannedDispenseTime);
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 临时医嘱执行 → 激活已有 DRAFT 收费明细。
     */
    @Override
    public ActivateChargeByExeCommand toActivateChargeByExeCommand(
        Long chargeItemId,
        Long procedureId,
        LocalDateTime performedAt,
        Long performerId) {
        ActivateChargeByExeCommand command = new ActivateChargeByExeCommand();
        ActivateChargeByExeCommand.Item line = new ActivateChargeByExeCommand.Item();
        line.setChargeItemId(chargeItemId);
        line.setProcedureId(procedureId);
        line.setPerformedAt(performedAt);
        line.setPerformerId(performerId);
        command.setItems(List.of(line));
        return command;
    }

    /**
     * 长期医嘱执行 → 新建待结算收费明细（generate_source=ORDER_EXECUTE）。
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
            // 复制签发侧费用模板：科目/机构等；药/诊金额须按执行 quantity/unit 与目录比例重算
            item.setDefinitionId(template.getDefinitionId());
            if (!productPricedCharge) {
                item.setUnitPrice(template.getUnitPrice());
                item.setQuantityUnit(template.getQuantityUnit());
                item.setBaseAmount(template.getBaseAmount());
                item.setDiscountAmount(template.getDiscountAmount());
                item.setSurchargeAmount(template.getSurchargeAmount());
            }
            if (StringUtils.isNotBlank(template.getProductTable())) {
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
            if (StringUtils.isNotBlank(template.getContextEnum())) {
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
            ProductDefinitionOrderChargePricingSupport.applyOrderChargePricing(
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
        ProductDefinitionOrderChargePricingSupport.applyOrderChargePricing(
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
        Long performLocation,
        Long encounterId,
        Long patientId,
        Long operatorId) {
        CreateExecutedDeviceOrderCommand command = new CreateExecutedDeviceOrderCommand();
        command.setOperatorId(operatorId);
        CreateExecutedDeviceOrderCommand.CreateExecutedDeviceOrderItemCommand line =
            new CreateExecutedDeviceOrderCommand.CreateExecutedDeviceOrderItemCommand();
        line.setGenerateSourceEnum(GenerateSource.ORDER_EXECUTE.getCode());
        line.setDeviceDefId(deviceDefId);
        line.setQuantity(quantity);
        line.setPerformLocation(performLocation);
        line.setEncounterId(encounterId);
        line.setPatientId(patientId);
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
     * 绑定耗材执行 → 执行直发已发放命令。
     */
    @Override
    public GenerateIssuedDeviceDispenseFromExecutionCommand toGenerateIssuedDeviceDispenseFromExecutionCommand(
        Long deviceReqId,
        Long procedureId,
        String lotNumber,
        String traceNo,
        BigDecimal quantity,
        Long operatorId) {
        GenerateIssuedDeviceDispenseFromExecutionCommand command = new GenerateIssuedDeviceDispenseFromExecutionCommand();
        command.setOperatorId(operatorId);
        GenerateIssuedDeviceDispenseFromExecutionCommand.GenerateIssuedDeviceDispenseFromExecutionItemCommand line =
            new GenerateIssuedDeviceDispenseFromExecutionCommand.GenerateIssuedDeviceDispenseFromExecutionItemCommand();
        line.setDeviceReqId(deviceReqId);
        line.setProcedureId(procedureId);
        line.setLotNumber(lotNumber);
        line.setTraceNo(traceNo);
        line.setDispenseQuantity(quantity);
        command.setItems(List.of(line));
        return command;
    }

    /**
     * 槽位列表中 planTime ≤ deadline 的项（BR-IP-EXE-011）。
     *
     * @param slots             源槽位列表
     * @param executionDeadline 截止时间
     * @return 窗口内计划点；源为空时返回空列表
     */
    private List<NurseStationOrderExecuteSlotDto> filterSlotsWithinDeadline(
        List<NurseStationOrderExecuteSlotDto> slots,
        LocalDateTime executionDeadline) {
        if (slots == null || slots.isEmpty()) {
            return Collections.emptyList();
        }
        List<NurseStationOrderExecuteSlotDto> filtered = new ArrayList<>();
        for (NurseStationOrderExecuteSlotDto slot : slots) {
            if (slot == null || slot.getPlanTime() == null) {
                continue;
            }
            if (!slot.getPlanTime().isAfter(executionDeadline)) {
                // planTime ≤ deadline 的计划点才出现在当前 Tab 列表
                filtered.add(slot);
            }
        }
        return filtered;
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
