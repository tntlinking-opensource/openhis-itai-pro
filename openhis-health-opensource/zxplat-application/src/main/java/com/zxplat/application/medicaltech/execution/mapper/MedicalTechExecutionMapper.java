/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.execution.mapper;

import com.zxplat.application.medicaltech.execution.dto.MedicalTechProcedureRecordDto;
import com.zxplat.application.medicaltech.execution.request.PageMedicalTechPatientQueueRequest;
import com.zxplat.application.medicaltech.execution.request.PageMedicalTechProcedureRecordsRequest;
import com.zxplat.application.medicaltech.execution.response.ListMedicalTechNetRemainingProceduresResponse;
import com.zxplat.application.medicaltech.execution.response.LoadMedicalTechExecutionSheetResponse;
import com.zxplat.application.medicaltech.execution.response.PageMedicalTechPatientQueueResponse;
import com.zxplat.application.medicaltech.execution.response.PageMedicalTechProcedureRecordsResponse;
import com.zxplat.application.medicaltech.execution.dto.MedicalTechExecutionLineDto;
import com.zxplat.application.medicaltech.execution.dto.MedicalTechPatientQueueItemDto;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.encounter.entity.Encounter;
import com.zxplat.domain.administration.encounter.queries.MedicalTechWorkstationQueueQuery;
import com.zxplat.domain.clinical.procedure.command.CancelProcedureRecordCommand;
import com.zxplat.domain.clinical.procedure.command.CompleteProcedureRecordCommand;
import com.zxplat.domain.clinical.procedure.command.CreateProcedureRecordCommand;
import com.zxplat.domain.clinical.procedure.command.StartProcedureExecutionCommand;
import com.zxplat.domain.clinical.procedure.queries.OrderExecutionSheetPageQuery;
import com.zxplat.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.zxplat.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 医技执行应用层装配：Request→Query/Command 与读模型/DTO→Response 字段搬运。
 *
 * <p>禁止承载业务流程判断；门闸与次数/同态口径在 {@code MedicalTechExecutionAppService}。</p>
 */
public interface MedicalTechExecutionMapper {

    /**
     * 队列请求 + 登录 TECH 科 → 领域队列查询。
     *
     * @param request         HTTP 请求
     * @param loginTechOrgId   登录医技科
     * @param visibleTechOrgIds 诊疗有活可见执行科室（共看组；可空则仅登录科）
     * @return 领域分页查询
     */
    MedicalTechWorkstationQueueQuery toQueueQuery(
        PageMedicalTechPatientQueueRequest request,
        Long loginTechOrgId,
        java.util.Collection<Long> visibleTechOrgIds);

    /**
     * Encounter 分页 + 已装配行 → 队列响应。
     *
     * @param page    领域分页
     * @param records 已 enrich 的队列行
     * @return 分页响应
     */
    PageMedicalTechPatientQueueResponse toPageQueueResponse(Page<Encounter> page, List<MedicalTechPatientQueueItemDto> records);

    /**
     * 执行单两列表 → 响应。
     *
     * @param serviceLines 诊疗行
     * @param deviceLines  耗材行
     * @return 执行单响应
     */
    LoadMedicalTechExecutionSheetResponse toSheetResponse(
        List<MedicalTechExecutionLineDto> serviceLines,
        List<MedicalTechExecutionLineDto> deviceLines);

    /**
     * 执行记录分页请求 → OrderExecutionSheet 分页查询。
     *
     * @param request HTTP 请求
     * @return 分页查询
     */
    OrderExecutionSheetPageQuery toOrderExecutionSheetPageQuery(PageMedicalTechProcedureRecordsRequest request);

    /**
     * 执行记录 DTO 列表 + Spring Page 元数据 → 分页响应。
     *
     * @param content 行
     * @param page    分页壳
     * @return 分页响应
     */
    PageMedicalTechProcedureRecordsResponse toPageProcedureRecordsResponse(
        List<MedicalTechProcedureRecordDto> content,
        Page<?> page);

    /**
     * 净剩记录 → 列表响应。
     *
     * @param records DTO 列表
     * @return 响应
     */
    ListMedicalTechNetRemainingProceduresResponse toNetRemainingResponse(List<MedicalTechProcedureRecordDto> records);

    /**
     * Procedure 读模型 → 记录 DTO（不含项目名/操作人 enrich）。
     *
     * @param row 读模型
     * @return DTO；row 空时 null
     */
    MedicalTechProcedureRecordDto toProcedureRecordDto(ProcedureRecordQueryModel row);

    /**
     * 构造按就诊装载收费明细命令。
     *
     * @param encounterId 就诊主键
     * @return 收费查询命令
     */
    ChargeItemQueryCommand toChargeItemQueryCommand(Long encounterId);

    /**
     * 构造创建执行记录命令（医技侧须再 setOrgId）。
     *
     * @param requestId      请求主键
     * @param requestTable   请求表
     * @param encounterId    就诊
     * @param patientId      患者
     * @param categoryEnum   类目
     * @param occurrenceTime 发生时刻
     * @param operatorId     操作人
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
     * 构造完成执行命令（结果摘要用医技占位常量）。
     *
     * @param procedureId 执行记录主键
     * @param operatorId  操作人
     * @return 完成命令
     */
    CompleteProcedureRecordCommand toCompleteProcedureRecordCommand(Long procedureId, Long operatorId);

    /**
     * 构造取消执行命令。
     *
     * @param procedureId                新 CANCEL 行主键
     * @param statusReasonText           取消原因
     * @param offsetCompletedProcedureId 被对冲 COMPLETED 主键
     * @param operatorId                 操作人
     * @return 取消命令
     */
    CancelProcedureRecordCommand toCancelProcedureRecordCommand(
        Long procedureId,
        String statusReasonText,
        Long offsetCompletedProcedureId,
        Long operatorId);

    /**
     * 构造库存回补命令。
     *
     * @param operatorId               操作人
     * @param inventoryIdToIncreaseQty 库存项 → 回补量
     * @return 回补命令
     */
    IncreaseQuantityCommand toIncreaseQuantityCommand(Long operatorId, Map<Long, BigDecimal> inventoryIdToIncreaseQty);
}
