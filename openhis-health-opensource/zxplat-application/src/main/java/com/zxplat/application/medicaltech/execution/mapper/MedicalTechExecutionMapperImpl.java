/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.execution.mapper;

import com.zxplat.application.medicaltech.execution.dto.MedicalTechExecutionLineDto;
import com.zxplat.application.medicaltech.execution.dto.MedicalTechPatientQueueItemDto;
import com.zxplat.application.medicaltech.execution.dto.MedicalTechProcedureRecordDto;
import com.zxplat.application.medicaltech.execution.request.PageMedicalTechPatientQueueRequest;
import com.zxplat.application.medicaltech.execution.request.PageMedicalTechProcedureRecordsRequest;
import com.zxplat.application.medicaltech.execution.response.ListMedicalTechNetRemainingProceduresResponse;
import com.zxplat.application.medicaltech.execution.response.LoadMedicalTechExecutionSheetResponse;
import com.zxplat.application.medicaltech.execution.response.PageMedicalTechPatientQueueResponse;
import com.zxplat.application.medicaltech.execution.response.PageMedicalTechProcedureRecordsResponse;
import com.zxplat.application.shared.pagination.PageQueryBinder;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.encounter.entity.Encounter;
import com.zxplat.domain.administration.encounter.queries.MedicalTechWorkstationQueueQuery;
import com.zxplat.domain.clinical.procedure.command.CancelProcedureRecordCommand;
import com.zxplat.domain.clinical.procedure.command.CompleteProcedureRecordCommand;
import com.zxplat.domain.clinical.procedure.command.CreateProcedureRecordCommand;
import com.zxplat.domain.clinical.procedure.command.StartProcedureExecutionCommand;
import com.zxplat.domain.clinical.procedure.queries.OrderExecutionSheetPageQuery;
import com.zxplat.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.zxplat.domain.share.constants.MedicalTechExecutionConstants;
import com.zxplat.domain.share.enums.EventStatus;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * {@link MedicalTechExecutionMapper} 实现：医技执行应用层字段映射。
 *
 * <p>对应 IF-MT-EXE-01～05、03B；null 安全返回空壳，禁止业务门闸。</p>
 */
@Component
@RequiredArgsConstructor
public class MedicalTechExecutionMapperImpl implements MedicalTechExecutionMapper {

    /**
     * 队列请求 + 登录 TECH 科 + 可见执行科室 → 领域队列查询。
     *
     * @param request           HTTP 请求
     * @param loginTechOrgId    登录医技科
     * @param visibleTechOrgIds 诊疗有活可见执行科室（共看组）
     * @return 领域分页查询
     */
    @Override
    public MedicalTechWorkstationQueueQuery toQueueQuery(
        PageMedicalTechPatientQueueRequest request,
        Long loginTechOrgId,
        Collection<Long> visibleTechOrgIds) {
        MedicalTechWorkstationQueueQuery query = new MedicalTechWorkstationQueueQuery();
        // Gate：请求空时仍返回可识别空查询，由 AppService 抛 EX
        if (request == null) {
            query.setLoginTechOrgId(loginTechOrgId);
            this.applyVisibleTechOrgIds(query, loginTechOrgId, visibleTechOrgIds);
            return query;
        }
        PageQueryBinder.bind(request, query);
        query.setLoginTechOrgId(loginTechOrgId);
        this.applyVisibleTechOrgIds(query, loginTechOrgId, visibleTechOrgIds);
        query.setEncounterScope(request.getEncounterScope());
        query.setPendingDeviceOnly(request.getPendingDeviceOnly());
        // 待执行/已执行分桶：空则领域 Spec 不按执行态收窄
        query.setExecutionQueueFilter(request.getExecutionQueueFilter());
        query.setInpatientStayFilter(request.getInpatientStayFilter());
        return query;
    }

    /**
     * 写入可见执行科室：入参优先，否则回落登录科单值。
     *
     * @param query             领域查询
     * @param loginTechOrgId    登录科
     * @param visibleTechOrgIds 共看可见集合
     */
    private void applyVisibleTechOrgIds(
        MedicalTechWorkstationQueueQuery query,
        Long loginTechOrgId,
        Collection<Long> visibleTechOrgIds) {
        List<Long> resolved = new ArrayList<>();
        if (visibleTechOrgIds != null) {
            for (Long orgId : visibleTechOrgIds) {
                if (orgId != null && !resolved.contains(orgId)) {
                    resolved.add(orgId);
                }
            }
        }
        if (resolved.isEmpty() && loginTechOrgId != null) {
            resolved.add(loginTechOrgId);
        }
        query.setVisibleTechOrgIds(resolved);
    }

    /**
     * Encounter 分页 + 已装配行 → 队列响应。
     *
     * @param page    领域分页
     * @param records 已 enrich 的队列行
     * @return 分页响应
     */
    @Override
    public PageMedicalTechPatientQueueResponse toPageQueueResponse(
        Page<Encounter> page,
        List<MedicalTechPatientQueueItemDto> records) {
        List<MedicalTechPatientQueueItemDto> content = records;
        if (content == null) {
            content = new ArrayList<>();
        }
        return PagedResultBuilder.build(new PageMedicalTechPatientQueueResponse(), content, page);
    }

    /**
     * 执行单两列表 → 响应。
     *
     * @param serviceLines 诊疗行
     * @param deviceLines  耗材行
     * @return 执行单响应
     */
    @Override
    public LoadMedicalTechExecutionSheetResponse toSheetResponse(
        List<MedicalTechExecutionLineDto> serviceLines,
        List<MedicalTechExecutionLineDto> deviceLines) {
        LoadMedicalTechExecutionSheetResponse response = new LoadMedicalTechExecutionSheetResponse();
        if (serviceLines == null) {
            response.setServiceLines(new ArrayList<>());
        } else {
            response.setServiceLines(serviceLines);
        }
        if (deviceLines == null) {
            response.setDeviceLines(new ArrayList<>());
        } else {
            response.setDeviceLines(deviceLines);
        }
        return response;
    }

    /**
     * 执行记录分页请求 → OrderExecutionSheet 分页查询。
     *
     * @param request HTTP 请求
     * @return 分页查询
     */
    @Override
    public OrderExecutionSheetPageQuery toOrderExecutionSheetPageQuery(
        PageMedicalTechProcedureRecordsRequest request) {
        OrderExecutionSheetPageQuery query = new OrderExecutionSheetPageQuery();
        if (request == null) {
            return query;
        }
        PageQueryBinder.bind(request, query);
        query.setEncounterId(request.getEncounterId());
        query.setRequestId(request.getRequestId());
        query.setStatusEnum(request.getStatusEnum());
        return query;
    }

    /**
     * 执行记录 DTO 列表 + Spring Page 元数据 → 分页响应。
     *
     * @param content 行
     * @param page    分页壳
     * @return 分页响应
     */
    @Override
    public PageMedicalTechProcedureRecordsResponse toPageProcedureRecordsResponse(
        List<MedicalTechProcedureRecordDto> content,
        Page<?> page) {
        List<MedicalTechProcedureRecordDto> safe = content;
        if (safe == null) {
            safe = new ArrayList<>();
        }
        return PagedResultBuilder.build(new PageMedicalTechProcedureRecordsResponse(), safe, page);
    }

    /**
     * 净剩记录 → 列表响应。
     *
     * @param records DTO 列表
     * @return 响应
     */
    @Override
    public ListMedicalTechNetRemainingProceduresResponse toNetRemainingResponse(
        List<MedicalTechProcedureRecordDto> records) {
        ListMedicalTechNetRemainingProceduresResponse response =
            new ListMedicalTechNetRemainingProceduresResponse();
        if (records == null) {
            response.setRecords(new ArrayList<>());
        } else {
            response.setRecords(records);
        }
        return response;
    }

    /**
     * Procedure 读模型 → 记录 DTO（不含项目名/操作人 enrich）。
     *
     * @param row 读模型
     * @return DTO；row 空时 null
     */
    @Override
    public MedicalTechProcedureRecordDto toProcedureRecordDto(ProcedureRecordQueryModel row) {
        if (row == null) {
            return null;
        }
        MedicalTechProcedureRecordDto dto = new MedicalTechProcedureRecordDto();
        dto.setProcedureId(row.getId());
        dto.setRequestId(row.getRequestId());
        dto.setStatusEnum(row.getStatusEnum());
        dto.setRecordedTime(row.getRecordedTime());
        EventStatus status = EnumUtil.fromCode(EventStatus.class, row.getStatusEnum());
        // Asm：动作文案由状态码推导，供抽屉展示
        if (status != null) {
            dto.setActionDisplayText(status.getDisplay());
        } else {
            dto.setActionDisplayText(row.getStatusEnum());
        }
        return dto;
    }

    /**
     * 构造按就诊装载收费明细命令。
     *
     * @param encounterId 就诊主键
     * @return 收费查询命令
     */
    @Override
    public ChargeItemQueryCommand toChargeItemQueryCommand(Long encounterId) {
        ChargeItemQueryCommand command = new ChargeItemQueryCommand();
        command.setEncounterId(encounterId);
        return command;
    }

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
    @Override
    public CreateProcedureRecordCommand toCreateProcedureRecordCommand(
        Long requestId,
        String requestTable,
        Long encounterId,
        Long patientId,
        String categoryEnum,
        LocalDateTime occurrenceTime,
        Long operatorId) {
        CreateProcedureRecordCommand command = new CreateProcedureRecordCommand();
        command.setRequestId(requestId);
        command.setRequestTable(requestTable);
        command.setEncounterId(encounterId);
        command.setPatientId(patientId);
        command.setCategoryEnum(categoryEnum);
        command.setOccurrenceTime(occurrenceTime);
        command.setOperatorId(operatorId);
        command.setSkipDuplicateRequestAnchorCheck(Boolean.TRUE);
        return command;
    }

    /**
     * 构造启动执行命令。
     *
     * @param procedureId 执行记录主键
     * @return 启动命令
     */
    @Override
    public StartProcedureExecutionCommand toStartProcedureExecutionCommand(Long procedureId) {
        StartProcedureExecutionCommand command = new StartProcedureExecutionCommand();
        command.setProcedureId(procedureId);
        return command;
    }

    /**
     * 构造完成执行命令（结果摘要用医技占位常量）。
     *
     * @param procedureId 执行记录主键
     * @param operatorId  操作人
     * @return 完成命令
     */
    @Override
    public CompleteProcedureRecordCommand toCompleteProcedureRecordCommand(Long procedureId, Long operatorId) {
        CompleteProcedureRecordCommand command = new CompleteProcedureRecordCommand();
        command.setProcedureId(procedureId);
        command.setOperatorId(operatorId);
        command.setResultText(MedicalTechExecutionConstants.MEDICAL_TECH_PROCEDURE_RESULT_TEXT);
        command.setReportJson(null);
        return command;
    }

    /**
     * 构造取消执行命令。
     *
     * @param procedureId                新 CANCEL 行主键
     * @param statusReasonText           取消原因
     * @param offsetCompletedProcedureId 被对冲 COMPLETED 主键
     * @param operatorId                 操作人
     * @return 取消命令
     */
    @Override
    public CancelProcedureRecordCommand toCancelProcedureRecordCommand(
        Long procedureId,
        String statusReasonText,
        Long offsetCompletedProcedureId,
        Long operatorId) {
        CancelProcedureRecordCommand command = new CancelProcedureRecordCommand();
        command.setProcedureId(procedureId);
        if (StringUtils.isEmpty(statusReasonText)) {
            command.setStatusReasonText("医技取消执行");
        } else {
            command.setStatusReasonText(statusReasonText);
        }
        command.setRefundId(offsetCompletedProcedureId);
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 构造库存回补命令。
     *
     * @param operatorId               操作人
     * @param inventoryIdToIncreaseQty 库存项 → 回补量
     * @return 回补命令
     */
    @Override
    public IncreaseQuantityCommand toIncreaseQuantityCommand(
        Long operatorId,
        Map<Long, BigDecimal> inventoryIdToIncreaseQty) {
        IncreaseQuantityCommand command = new IncreaseQuantityCommand();
        command.setOperatorId(operatorId);
        List<IncreaseQuantityCommand.IncreaseQuantityItemCommand> items = new ArrayList<>();
        if (inventoryIdToIncreaseQty != null) {
            for (Map.Entry<Long, BigDecimal> entry : inventoryIdToIncreaseQty.entrySet()) {
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
}
