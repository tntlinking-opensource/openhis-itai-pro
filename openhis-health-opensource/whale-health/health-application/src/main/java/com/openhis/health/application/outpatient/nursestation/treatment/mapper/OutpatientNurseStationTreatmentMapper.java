/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.nursestation.treatment.mapper;

import com.openhis.health.application.outpatient.nursestation.treatment.dto.NurseStationTreatmentLineDto;
import com.openhis.health.application.outpatient.nursestation.treatment.request.ListProcedureRecordsForNurseStationRequest;
import com.openhis.health.application.outpatient.nursestation.treatment.response.ListNetRemainingProcedureRecordsForNurseStationResponse;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.application.outpatient.nursestation.treatment.request.QueryEncounterTreatmentSheetRequest;
import com.openhis.health.application.outpatient.nursestation.treatment.response.ListProcedureRecordsForNurseStationResponse;
import com.openhis.health.application.outpatient.nursestation.treatment.response.QueryEncounterTreatmentSheetResponse;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.clinical.procedure.command.CancelProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.command.CompleteProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.command.CreateProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.command.StartProcedureExecutionCommand;
import com.openhis.health.domain.workflow.devicedispense.command.RevertCompletedBatchToPreparationCommand;
import com.openhis.health.domain.workflow.devicerequest.command.RevokeDeviceOrderExecutionCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.clinical.procedure.queries.OrderExecutionSheetPageQuery;
import com.openhis.health.domain.clinical.procedure.queries.model.OrderExecutionSheetQueryModel;
import com.openhis.health.domain.workflow.devicerequest.command.ExecuteDeviceOrderCommand;
import com.openhis.health.domain.workflow.devicedispense.command.DispenseDeviceCommand;
import com.openhis.health.domain.workflow.devicedispense.command.MatchDispenseLotNumberCommand;
import com.openhis.health.domain.workflow.devicedispense.command.PrepareDeviceDispenseCommand;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 门诊护士站处置应用层装配：OrderExecutionSheet 读模型与处置编排命令、DTO/Response 之间的字段搬运。
 * <p>禁止承载业务流程判断；门闸与次数口径在 {@code OutpatientNurseStationTreatmentAppService}。</p>
 */
public interface OutpatientNurseStationTreatmentMapper {

    /**
     * 组装处置单三列表响应容器。
     *
     * @param request        原始请求（保留 encounterId 等审计旁路字段）
     * @param encounter      就诊详情（头信息）
     * @param serviceLines   诊疗行
     * @param medicationLines 药品行
     * @param deviceLines    耗材行
     * @return 三列表响应
     */
    QueryEncounterTreatmentSheetResponse toQueryEncounterTreatmentSheetResponse(
        QueryEncounterTreatmentSheetRequest request,
        EncounterDetailQueryModel encounter,
        List<NurseStationTreatmentLineDto> serviceLines,
        List<NurseStationTreatmentLineDto> medicationLines,
        List<NurseStationTreatmentLineDto> deviceLines);

    /**
     * 将执行记录抽屉请求映射为医嘱执行单读模型分页查询。
     *
     * @param request HTTP 请求；调用方保证非空
     * @return 读模型分页查询
     */
    OrderExecutionSheetPageQuery toOrderExecutionSheetPageQuery(ListProcedureRecordsForNurseStationRequest request);

    /**
     * 将医嘱执行单读模型分页映射为抽屉列表响应。
     *
     * @param page 读模型分页
     * @return 抽屉响应
     */
    ListProcedureRecordsForNurseStationResponse toListProcedureRecordsForNurseStationResponse(Page<OrderExecutionSheetQueryModel> page);

    /**
     * 将净剩可取消执行记录查询模型列表装配为 IF-NUR-03B 响应。
     *
     * @param netRows 域查询净剩 COMPLETED 行；允许 null
     * @return 列表响应
     */
    ListNetRemainingProcedureRecordsForNurseStationResponse toListNetRemainingProcedureRecordsForNurseStationResponse(
        List<ProcedureRecordQueryModel> netRows);

    /**
     * 构造按就诊装载收费明细命令。
     *
     * @param encounterId 就诊主键；调用方保证非空
     * @return 收费查询命令
     */
    ChargeItemQueryCommand toChargeItemQueryCommand(Long encounterId);

    /**
     * 构造创建执行记录命令。
     *
     * @param requestId     请求主键
     * @param requestTable  请求表持久化名
     * @param encounterId   就诊主键
     * @param patientId     患者主键
     * @param categoryEnum  执行类目码
     * @param occurrenceTime 发生时刻
     * @param operatorId    操作人
     * @return 创建命令
     */
    CreateProcedureRecordCommand toCreateProcedureRecordCommand(
        Long requestId,
        String requestTable,
        Long encounterId,
        Long patientId,
        String categoryEnum,
        LocalDateTime occurrenceTime,
        Long operatorId);

    /**
     * 构造启动执行命令。
     *
     * @param procedureId 执行记录主键
     * @return 启动命令
     */
    StartProcedureExecutionCommand toStartProcedureExecutionCommand(Long procedureId);

    /**
     * 构造完成执行命令（结果摘要由护士站占位常量填充以满足完成门禁）。
     *
     * @param procedureId 执行记录主键
     * @param operatorId  操作人主键
     * @return 完成命令
     */
    CompleteProcedureRecordCommand toCompleteProcedureRecordCommand(Long procedureId, Long operatorId);

    /**
     * 构造耗材医嘱执行确认命令（单条批次语义，与领域服务一致）。
     *
     * @param deviceRequestId 耗材请求主键
     * @param performerId     执行人
     * @param performOrgId    执行机构
     * @param operatorId      操作人
     * @return 执行确认命令
     */
    ExecuteDeviceOrderCommand toExecuteDeviceOrderCommand(
        Long deviceRequestId,
        Long performerId,
        Long performOrgId,
        Long operatorId);

    /**
     * 构造耗材发放拣配命令（单条批次语义）。
     *
     * @param dispenseId   发放行主键
     * @param preparerId   拣配人
     * @param lotNumber      批号
     * @param traceNo        追溯码
     * @param quantity       数量
     * @param operatorId     操作人
     * @return 拣配命令
     */
    PrepareDeviceDispenseCommand toPrepareDeviceDispenseCommand(
        Long dispenseId,
        Long preparerId,
        String lotNumber,
        String traceNo,
        BigDecimal quantity,
        Long operatorId);

    /**
     * 构造批号匹配门控命令（灵活定位键为单发放主键字符串）。
     *
     * @param idListOrRequestKey 发放定位键；调用方保证非空
     * @param operatorId         操作人主键
     * @return 批号匹配命令
     */
    MatchDispenseLotNumberCommand toMatchDispenseLotNumberCommand(String idListOrRequestKey, Long operatorId);

    /**
     * 构造核对发放命令（单条批次语义）。
     *
     * @param dispenseId  发放行主键
     * @param performerId 发放人
     * @param quantity    发放数量
     * @param operatorId  操作人
     * @return 核对发放命令
     */
    DispenseDeviceCommand toDispenseDeviceCommand(Long dispenseId, Long performerId, BigDecimal quantity, Long operatorId);

    /**
     * 构造取消执行命令（护士站追加 CANCEL 记录路径）。
     *
     * @param procedureId      执行记录主键
     * @param statusReasonText 取消原因
     * @param refundId         被抵消的已完成执行记录主键（{@code cln_procedure.id}，BR-PRO-005 必填）
     * @param operatorId       操作人
     * @return 取消命令
     */
    CancelProcedureRecordCommand toCancelProcedureRecordCommand(
        Long procedureId,
        String statusReasonText,
        Long refundId,
        Long operatorId);

    /**
     * 构造已发放批量回退为待发命令。
     *
     * @param dispenseIds 发放行主键列表
     * @param operatorId  操作人
     * @return 回退命令
     */
    RevertCompletedBatchToPreparationCommand toRevertCompletedBatchToPreparationCommand(
        List<Long> dispenseIds,
        Long operatorId);

    /**
     * 构造撤销耗材医嘱执行确认命令（单条批次语义）。
     *
     * @param deviceRequestId 耗材请求主键
     * @param operatorId      操作人
     * @return 撤销命令
     */
    RevokeDeviceOrderExecutionCommand toRevokeDeviceOrderExecutionCommand(Long deviceRequestId, Long operatorId);

    /**
     * 构造库存数量增加命令（批量语义，与 IF-NUR-04 扣减对称）。
     *
     * @param operatorId           操作人
     * @param inventoryIdToIncreaseQty 库存项主键 → 增加数量
     * @return 增加命令；映射为空时 items 为空列表
     */
    IncreaseQuantityCommand toIncreaseQuantityCommand(Long operatorId, Map<Long, BigDecimal> inventoryIdToIncreaseQty);
}
