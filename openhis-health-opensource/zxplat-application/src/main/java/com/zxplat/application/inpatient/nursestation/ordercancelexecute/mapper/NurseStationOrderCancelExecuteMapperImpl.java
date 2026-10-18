/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.ordercancelexecute.mapper;

import com.zxplat.application.inpatient.nursestation.dispensesummary.assembler.NurseStationDispenseSummaryWriteAssembler.SummaryMergeGroup;
import com.zxplat.application.inpatient.nursestation.dispensesummary.mapper.NurseStationDispenseSummaryMapper;
import com.zxplat.application.inpatient.nursestation.ordercancelexecute.response.CancelNurseStationOrderExecutionsResponse;
import com.zxplat.domain.administration.chargeitem.command.VoidOrderExecuteChargeItemsByProcedureIdsCommand;
import com.zxplat.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.zxplat.domain.clinical.procedure.command.CancelProcedureRecordCommand;
import com.zxplat.domain.clinical.procedure.command.CreateProcedureRecordCommand;
import com.zxplat.domain.clinical.procedure.command.StartProcedureExecutionCommand;
import com.zxplat.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.zxplat.domain.medication.medicationdispense.command.CancelMedicationDispenseSummaryCommand;
import com.zxplat.domain.medication.medicationdispense.command.CreatePendingRefundDispenseCommand;
import com.zxplat.domain.medication.medicationdispense.command.RemoveUndispensedDispenseCommand;
import com.zxplat.domain.medication.medicationdispense.command.SummarizeMedicationDispensesCommand;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.zxplat.domain.workflow.servicerequest.command.RemoveExecutedServiceRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateSummarySupplyRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * {@link NurseStationOrderCancelExecuteMapper} 实现：字段搬运；禁止承载业务流程判断。
 */
@Component
@RequiredArgsConstructor
public class NurseStationOrderCancelExecuteMapperImpl implements NurseStationOrderCancelExecuteMapper {

    /** 汇总过滤重建映射器（撤汇总/重建命令复用）。 */
    private final NurseStationDispenseSummaryMapper nurseStationDispenseSummaryMapper;

    /**
     * 护士站可见就诊查询入参（管辖病区 + 前端勾选 encounterIds）。
     *
     * @param encounterIds    前端勾选就诊
     * @param managedWardIds  管辖病区 location id
     * @return 住院就诊分页查询
     */
    @Override
    public InpatientEncounterPageQuery toInpatientEncounterPageQuery(List<Long> encounterIds, List<Long> managedWardIds) {
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        // 护士站可见性谓词：状态并集 + 管理病区 + encounterIdsIn 收窄（BR-IP-CXL-001）。
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
     * 已完成执行记录 → 创建 CANCEL 对冲 Procedure 命令。
     *
     * @param completedRow 净剩 COMPLETED 行
     * @param operatorId   操作人
     * @param now          操作时刻
     * @return 创建命令
     */
    @Override
    public CreateProcedureRecordCommand toCreateProcedureRecordCommand(
        ProcedureRecordQueryModel completedRow,
        Long operatorId,
        LocalDateTime now) {
        if (completedRow == null) {
            return null;
        }
        CreateProcedureRecordCommand command = new CreateProcedureRecordCommand();
        command.setRequestId(completedRow.getRequestId());
        command.setRequestTable(completedRow.getRequestTable());
        command.setEncounterId(completedRow.getEncounterId());
        command.setPatientId(completedRow.getPatientId());
        command.setCategoryEnum(completedRow.getCategoryEnum());
        LocalDateTime occurrenceTime = completedRow.getOccurrenceTime();
        if (occurrenceTime == null) {
            occurrenceTime = now;
        }
        command.setOccurrenceTime(occurrenceTime);
        command.setOperatorId(operatorId);
        command.setSkipDuplicateRequestAnchorCheck(Boolean.TRUE);
        // CANCEL 对冲须继承 COMPLETED occurrence；被对冲行仍占槽，建单阶段跳过占用 Gate
        command.setSkipOccurrenceSlotCheck(Boolean.TRUE);
        // CANCEL 对冲须同侧：继承被对冲 COMPLETED 的执行科室
        command.setOrgId(completedRow.getOrgId());
        return command;
    }

    /**
     * 构造启动执行命令。
     *
     * @param newProcedureId 新 Procedure 主键
     * @return 启动命令
     */
    @Override
    public StartProcedureExecutionCommand toStartProcedureExecutionCommand(Long newProcedureId) {
        StartProcedureExecutionCommand command = new StartProcedureExecutionCommand();
        command.setProcedureId(newProcedureId);
        return command;
    }

    /**
     * 构造取消执行命令（refund_id 指向被对冲 COMPLETED 行）。
     *
     * @param newProcedureId            新 CANCEL 行主键
     * @param statusReasonText          取消原因
     * @param offsetCompletedProcedureId 被对冲 COMPLETED 行主键
     * @param operatorId                操作人
     * @return 取消命令
     */
    @Override
    public CancelProcedureRecordCommand toCancelProcedureRecordCommand(
        Long newProcedureId,
        String statusReasonText,
        Long offsetCompletedProcedureId,
        Long operatorId) {
        CancelProcedureRecordCommand command = new CancelProcedureRecordCommand();
        command.setProcedureId(newProcedureId);
        command.setStatusReasonText(statusReasonText);
        command.setRefundId(offsetCompletedProcedureId);
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 长期执行新建 Charge 逻辑删命令。
     *
     * @param procedureIds 执行记录主键集合
     * @param operatorId   操作人
     * @return 逻辑删命令
     */
    @Override
    public VoidOrderExecuteChargeItemsByProcedureIdsCommand toVoidOrderExecuteChargeItemsByProcedureIdsCommand(
        Collection<Long> procedureIds,
        Long operatorId) {
        VoidOrderExecuteChargeItemsByProcedureIdsCommand command = new VoidOrderExecuteChargeItemsByProcedureIdsCommand();
        command.setOperatorId(operatorId);
        command.setProcedureIds(procedureIds);
        return command;
    }

    /**
     * 逻辑删除执行派生 ServiceRequest 命令。
     *
     * @param serviceRequestIds 派生诊疗申请主键
     * @param operatorId        操作人
     * @return 删除命令
     */
    @Override
    public RemoveExecutedServiceRequestCommand toRemoveExecutedServiceRequestCommand(
        List<Long> serviceRequestIds,
        Long operatorId) {
        RemoveExecutedServiceRequestCommand command = new RemoveExecutedServiceRequestCommand();
        command.setOperatorId(operatorId);
        List<RemoveExecutedServiceRequestCommand.Item> items = new ArrayList<>();
        if (serviceRequestIds != null) {
            for (Long serviceRequestId : serviceRequestIds) {
                if (serviceRequestId == null) {
                    continue;
                }
                RemoveExecutedServiceRequestCommand.Item item = new RemoveExecutedServiceRequestCommand.Item();
                item.setId(serviceRequestId);
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /**
     * 软删未发药发放行命令（长临同构，BR-IP-CXL-007）。
     *
     * @param dispenseId 发放行主键
     * @param operatorId 操作人
     * @return 删除命令
     */
    @Override
    public RemoveUndispensedDispenseCommand toRemoveUndispensedDispenseCommand(Long dispenseId, Long operatorId) {
        RemoveUndispensedDispenseCommand command = new RemoveUndispensedDispenseCommand();
        command.setOperatorId(operatorId);
        List<Long> ids = new ArrayList<>();
        ids.add(dispenseId);
        command.setIds(ids);
        return command;
    }

    /**
     * 已发药路径创建待退药发放单命令。
     *
     * @param originDispenseId   原发放行主键
     * @param medReqId           原发放药嘱主键
     * @param cancelProcedureId  新 CANCEL 行主键
     * @param qty                退药数量
     * @param operatorId         操作人
     * @return 待退药创建命令
     */
    @Override
    public CreatePendingRefundDispenseCommand toCreatePendingRefundDispenseCommand(
        Long originDispenseId,
        Long medReqId,
        Long cancelProcedureId,
        BigDecimal qty,
        Long operatorId) {
        CreatePendingRefundDispenseCommand command = new CreatePendingRefundDispenseCommand();
        command.setOperatorId(operatorId);
        command.setOriginDispenseId(originDispenseId);
        command.setRefundRequestId(medReqId);
        command.setProcedureId(cancelProcedureId);
        command.setRefundQuantity(qty);
        return command;
    }

    /**
     * 取消汇总发放命令。
     *
     * @param summaryNo  汇总单号
     * @param operatorId 操作人
     * @return 取消汇总命令
     */
    @Override
    public CancelMedicationDispenseSummaryCommand toCancelMedicationDispenseSummaryCommand(
        String summaryNo,
        Long operatorId) {
        return nurseStationDispenseSummaryMapper.toCancelMedicationDispenseSummaryCommand(summaryNo, operatorId);
    }

    /**
     * 按 busNo 逻辑删除汇总 Supply 命令。
     *
     * @param summaryNo  汇总单号
     * @param operatorId 操作人
     * @return 删除命令
     */
    @Override
    public RemoveByBusNoCommand toRemoveByBusNoCommand(String summaryNo, Long operatorId) {
        return nurseStationDispenseSummaryMapper.toRemoveByBusNoCommand(summaryNo, operatorId);
    }

    /**
     * 汇总 Supply 自动驳回命令（APPROVAL 路径）。
     *
     * @param summaryNo  汇总单号
     * @param operatorId 操作人
     * @return 驳回命令
     */
    @Override
    public RejectRequestCommand toRejectRequestCommand(String summaryNo, Long operatorId) {
        RejectRequestCommand command = new RejectRequestCommand();
        command.setOperatorId(operatorId);
        RejectRequestCommand.RejectRequestItemCommand item = new RejectRequestCommand.RejectRequestItemCommand();
        item.setBusNo(summaryNo);
        item.setApproverId(operatorId);
        item.setChangeReasonCode(InpatientNurseStationConstants.ORDER_CANCEL_EXECUTE_SUPPLY_REJECT_REASON_CODE);
        command.setItems(List.of(item));
        return command;
    }

    /**
     * 发放行汇总命令。
     *
     * @param locationId  发放药房
     * @param dispenseIds 发放行 id 列表
     * @param operatorId  操作人
     * @return 汇总命令
     */
    @Override
    public SummarizeMedicationDispensesCommand toSummarizeMedicationDispensesCommand(
        Long locationId,
        List<Long> dispenseIds,
        Long operatorId,
        String summaryNo) {
        return nurseStationDispenseSummaryMapper.toSummarizeMedicationDispensesCommand(
            locationId, dispenseIds, operatorId, summaryNo);
    }

    /**
     * 创建汇总 SupplyRequest 命令（初态 PENDING_APPROVAL）。
     *
     * @param summaryNo   汇总单号
     * @param groups      合并组
     * @param applicantId 申请人（医务人员）
     * @param operatorId  操作人
     * @return 创建命令
     */
    @Override
    public CreateSummarySupplyRequestCommand toCreateSummarySupplyRequestCommand(
        String summaryNo,
        List<SummaryMergeGroup> groups,
        Long applicantId,
        Long operatorId) {
        return nurseStationDispenseSummaryMapper.toCreateSummarySupplyRequestCommand(
            summaryNo, groups, applicantId, operatorId, null);
    }

    /**
     * 库存回补命令。
     *
     * @param operatorId          操作人
     * @param inventoryIdToQty  库存项 → 回补量
     * @return 增加库存命令
     */
    @Override
    public IncreaseQuantityCommand toIncreaseQuantityCommand(
        Long operatorId,
        Map<Long, BigDecimal> inventoryIdToQty) {
        IncreaseQuantityCommand command = new IncreaseQuantityCommand();
        command.setOperatorId(operatorId);
        List<IncreaseQuantityCommand.IncreaseQuantityItemCommand> items = new ArrayList<>();
        if (inventoryIdToQty != null) {
            for (Map.Entry<Long, BigDecimal> entry : inventoryIdToQty.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                if (entry.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                IncreaseQuantityCommand.IncreaseQuantityItemCommand item =
                    new IncreaseQuantityCommand.IncreaseQuantityItemCommand();
                item.setId(entry.getKey());
                item.setIncreaseQty(entry.getValue());
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /**
     * IF-IP-CXL-01 写成功响应。
     *
     * @param cancelledProcedureIds 成功对冲的原 COMPLETED id
     * @param rebuiltSummaryNos     过滤重建的新 summaryNo
     * @return 响应
     */
    @Override
    public CancelNurseStationOrderExecutionsResponse toCancelNurseStationOrderExecutionsResponse(
        List<Long> cancelledProcedureIds,
        List<String> rebuiltSummaryNos) {
        CancelNurseStationOrderExecutionsResponse response = new CancelNurseStationOrderExecutionsResponse();
        List<Long> safeCancelled = cancelledProcedureIds;
        if (safeCancelled == null) {
            safeCancelled = new ArrayList<>();
        }
        List<String> safeRebuilt = rebuiltSummaryNos;
        if (safeRebuilt == null) {
            safeRebuilt = new ArrayList<>();
        }
        response.setSuccess(Boolean.TRUE);
        response.setCancelledProcedureIds(safeCancelled);
        response.setRebuiltSummaryNos(safeRebuilt);
        if (safeRebuilt.isEmpty()) {
            response.setMessage(InpatientNurseStationConstants.ORDER_CANCEL_EXECUTE_SUCCESS_MESSAGE);
        } else {
            response.setMessage(InpatientNurseStationConstants.ORDER_CANCEL_EXECUTE_REBUILD_SUCCESS_MESSAGE);
        }
        return response;
    }
}
