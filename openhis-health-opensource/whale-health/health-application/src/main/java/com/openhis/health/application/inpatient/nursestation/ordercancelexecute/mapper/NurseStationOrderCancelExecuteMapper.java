/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ordercancelexecute.mapper;

import com.openhis.health.application.inpatient.nursestation.dispensesummary.assembler.NurseStationDispenseSummaryWriteAssembler.SummaryMergeGroup;
import com.openhis.health.application.inpatient.nursestation.ordercancelexecute.response.CancelNurseStationOrderExecutionsResponse;
import com.openhis.health.domain.administration.chargeitem.command.VoidOrderExecuteChargeItemsByProcedureIdsCommand;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.clinical.procedure.command.CancelProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.command.CreateProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.command.StartProcedureExecutionCommand;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.medication.medicationdispense.command.CancelMedicationDispenseSummaryCommand;
import com.openhis.health.domain.medication.medicationdispense.command.CreatePendingRefundDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.command.RemoveUndispensedDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.command.SummarizeMedicationDispensesCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.servicerequest.command.RemoveExecutedServiceRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateSummarySupplyRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 住院护士站取消执行映射器：Request/QueryModel → Command，领域结果 → Response。
 *
 * <p>禁止承载业务流程判断与 Gate 逻辑。</p>
 */
public interface NurseStationOrderCancelExecuteMapper {

    /**
     * 护士站可见就诊查询入参（管辖病区 + 前端勾选 encounterIds）。
     *
     * @param encounterIds    前端勾选就诊
     * @param managedWardIds  管辖病区 location id
     * @return 住院就诊分页查询
     */
    InpatientEncounterPageQuery toInpatientEncounterPageQuery(List<Long> encounterIds, List<Long> managedWardIds);

    /**
     * 已完成执行记录 → 创建 CANCEL 对冲 Procedure 命令。
     *
     * @param completedRow 净剩 COMPLETED 行
     * @param operatorId   操作人
     * @param now          操作时刻
     * @return 创建命令
     */
    CreateProcedureRecordCommand toCreateProcedureRecordCommand(
        ProcedureRecordQueryModel completedRow,
        Long operatorId,
        LocalDateTime now);

    /**
     * 构造启动执行命令。
     *
     * @param newProcedureId 新 Procedure 主键
     * @return 启动命令
     */
    StartProcedureExecutionCommand toStartProcedureExecutionCommand(Long newProcedureId);

    /**
     * 构造取消执行命令（refund_id 指向被对冲 COMPLETED 行）。
     *
     * @param newProcedureId            新 CANCEL 行主键
     * @param statusReasonText          取消原因
     * @param offsetCompletedProcedureId 被对冲 COMPLETED 行主键
     * @param operatorId                操作人
     * @return 取消命令
     */
    CancelProcedureRecordCommand toCancelProcedureRecordCommand(
        Long newProcedureId,
        String statusReasonText,
        Long offsetCompletedProcedureId,
        Long operatorId);

    /**
     * 长期执行新建 Charge 逻辑删命令。
     *
     * @param procedureIds 执行记录主键集合
     * @param operatorId   操作人
     * @return 逻辑删命令
     */
    VoidOrderExecuteChargeItemsByProcedureIdsCommand toVoidOrderExecuteChargeItemsByProcedureIdsCommand(
        Collection<Long> procedureIds,
        Long operatorId);

    /**
     * 逻辑删除执行派生 ServiceRequest 命令。
     *
     * @param serviceRequestIds 派生诊疗申请主键
     * @param operatorId        操作人
     * @return 删除命令
     */
    RemoveExecutedServiceRequestCommand toRemoveExecutedServiceRequestCommand(
        List<Long> serviceRequestIds,
        Long operatorId);

    /**
     * 软删未发药发放行命令（长临同构，BR-IP-CXL-007）。
     *
     * @param dispenseId 发放行主键
     * @param operatorId 操作人
     * @return 删除命令
     */
    RemoveUndispensedDispenseCommand toRemoveUndispensedDispenseCommand(Long dispenseId, Long operatorId);

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
    CreatePendingRefundDispenseCommand toCreatePendingRefundDispenseCommand(
        Long originDispenseId,
        Long medReqId,
        Long cancelProcedureId,
        BigDecimal qty,
        Long operatorId);

    /**
     * 取消汇总发放命令。
     *
     * @param summaryNo  汇总单号
     * @param operatorId 操作人
     * @return 取消汇总命令
     */
    CancelMedicationDispenseSummaryCommand toCancelMedicationDispenseSummaryCommand(String summaryNo, Long operatorId);

    /**
     * 按 busNo 逻辑删除汇总 Supply 命令。
     *
     * @param summaryNo  汇总单号
     * @param operatorId 操作人
     * @return 删除命令
     */
    RemoveByBusNoCommand toRemoveByBusNoCommand(String summaryNo, Long operatorId);

    /**
     * 汇总 Supply 自动驳回命令（APPROVAL 路径）。
     *
     * @param summaryNo  汇总单号
     * @param operatorId 操作人
     * @return 驳回命令
     */
    RejectRequestCommand toRejectRequestCommand(String summaryNo, Long operatorId);

    /**
     * 发放行汇总命令。
     *
     * @param locationId  发放药房
     * @param dispenseIds 发放行 id 列表
     * @param operatorId  操作人
     * @return 汇总命令
     */
    SummarizeMedicationDispensesCommand toSummarizeMedicationDispensesCommand(
        Long locationId,
        List<Long> dispenseIds,
        Long operatorId,
        String summaryNo);

    /**
     * 创建汇总 SupplyRequest 命令（初态 PENDING_APPROVAL）。
     *
     * @param summaryNo   汇总单号
     * @param groups      合并组
     * @param applicantId 申请人（医务人员）
     * @param operatorId  操作人
     * @return 创建命令
     */
    CreateSummarySupplyRequestCommand toCreateSummarySupplyRequestCommand(
        String summaryNo,
        List<SummaryMergeGroup> groups,
        Long applicantId,
        Long operatorId);

    /**
     * 库存回补命令。
     *
     * @param operatorId          操作人
     * @param inventoryIdToQty  库存项 → 回补量
     * @return 增加库存命令
     */
    IncreaseQuantityCommand toIncreaseQuantityCommand(Long operatorId, Map<Long, BigDecimal> inventoryIdToQty);

    /**
     * IF-IP-CXL-01 写成功响应。
     *
     * @param cancelledProcedureIds 成功对冲的原 COMPLETED id
     * @param rebuiltSummaryNos     过滤重建的新 summaryNo
     * @return 响应
     */
    CancelNurseStationOrderExecutionsResponse toCancelNurseStationOrderExecutionsResponse(
        List<Long> cancelledProcedureIds,
        List<String> rebuiltSummaryNos);
}
