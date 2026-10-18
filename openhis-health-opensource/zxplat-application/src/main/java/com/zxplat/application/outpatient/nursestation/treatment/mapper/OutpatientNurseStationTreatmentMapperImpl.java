/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.treatment.mapper;

import com.zxplat.application.shared.pagination.PageQueryBinder;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.domain.share.constants.OutpatientNurseStationConstants;
import com.zxplat.application.outpatient.nursestation.treatment.dto.NurseStationTreatmentLineDto;
import com.zxplat.application.outpatient.nursestation.treatment.dto.NetRemainingProcedureRecordItemDto;
import com.zxplat.application.outpatient.nursestation.treatment.dto.OrderExecutionSheetItemDto;
import com.zxplat.application.outpatient.nursestation.treatment.response.ListNetRemainingProcedureRecordsForNurseStationResponse;
import com.zxplat.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.zxplat.application.outpatient.nursestation.treatment.request.ListProcedureRecordsForNurseStationRequest;
import com.zxplat.application.outpatient.nursestation.treatment.request.QueryEncounterTreatmentSheetRequest;
import com.zxplat.application.outpatient.nursestation.treatment.response.ListProcedureRecordsForNurseStationResponse;
import com.zxplat.application.outpatient.nursestation.treatment.response.QueryEncounterTreatmentSheetResponse;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.zxplat.domain.clinical.procedure.command.CancelProcedureRecordCommand;
import com.zxplat.domain.clinical.procedure.command.CompleteProcedureRecordCommand;
import com.zxplat.domain.clinical.procedure.command.CreateProcedureRecordCommand;
import com.zxplat.domain.clinical.procedure.command.StartProcedureExecutionCommand;
import com.zxplat.domain.workflow.devicedispense.command.RevertCompletedBatchToPreparationCommand;
import com.zxplat.domain.workflow.devicerequest.command.RevokeDeviceOrderExecutionCommand;
import com.zxplat.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.zxplat.domain.clinical.procedure.queries.OrderExecutionSheetPageQuery;
import com.zxplat.domain.clinical.procedure.queries.model.OrderExecutionSheetQueryModel;
import com.zxplat.domain.share.enums.EventStatus;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.workflow.devicerequest.command.ExecuteDeviceOrderCommand;
import com.zxplat.domain.workflow.devicedispense.command.DispenseDeviceCommand;
import com.zxplat.domain.workflow.devicedispense.command.MatchDispenseLotNumberCommand;
import com.zxplat.domain.workflow.devicedispense.command.PrepareDeviceDispenseCommand;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link OutpatientNurseStationTreatmentMapper} 实现：门诊护士站处置应用层字段映射器。
 *
 * <p><b>职责</b>：将处置单查询、执行记录查询等 HTTP 请求装配为读模型查询命令，
 * 将读模型装配为 HTTP 响应；将执行操作请求（药品/耗材发放、执行记录创建/完成等）装配为领域命令。</p>
 *
 * <p><b>对应接口规格</b>：IF-NUR-02 ～ IF-NUR-05、IF-NUR-03B。</p>
 *
 * <p><b>边界假设</b>：
 * <ul>
 *   <li>null 安全：读模型分页/列表为 null 时返回空壳响应（空列表+0总数），避免前端判 null。</li>
 *   <li>性别枚举转换使用 {@link EnumUtil}，码表与前端约定保持一致。</li>
 *   <li>命令装配不处理业务规则（如库存扣减、状态校验），由领域服务层负责。</li>
 * </ul></p>
 */
@Component
public class OutpatientNurseStationTreatmentMapperImpl implements OutpatientNurseStationTreatmentMapper {

    /**
     * 组装处置单三列表响应容器。
     *
     * @param request         原始请求（保留 encounterId 等审计旁路字段）
     * @param encounter       就诊详情（头信息）
     * @param serviceLines    诊疗行
     * @param medicationLines 药品行
     * @param deviceLines     耗材行
     * @return 三列表响应
     */
    @Override
    public QueryEncounterTreatmentSheetResponse toQueryEncounterTreatmentSheetResponse(
        QueryEncounterTreatmentSheetRequest request,
        EncounterDetailQueryModel encounter,
        List<NurseStationTreatmentLineDto> serviceLines,
        List<NurseStationTreatmentLineDto> medicationLines,
        List<NurseStationTreatmentLineDto> deviceLines) {
        QueryEncounterTreatmentSheetResponse response = new QueryEncounterTreatmentSheetResponse();
        if (serviceLines == null) {
            response.setServiceLines(new ArrayList<>());
        } else {
            response.setServiceLines(serviceLines);
        }
        if (medicationLines == null) {
            response.setMedicationLines(new ArrayList<>());
        } else {
            response.setMedicationLines(medicationLines);
        }
        if (deviceLines == null) {
            response.setDeviceLines(new ArrayList<>());
        } else {
            response.setDeviceLines(deviceLines);
        }
        return response;
    }

    /**
     * 将执行记录抽屉请求映射为医嘱执行单读模型分页查询。
     *
     * @param request HTTP 请求；调用方保证非空
     * @return 读模型分页查询
     */
    @Override
    public OrderExecutionSheetPageQuery toOrderExecutionSheetPageQuery(ListProcedureRecordsForNurseStationRequest request) {
        OrderExecutionSheetPageQuery query = new OrderExecutionSheetPageQuery();
        if (request == null) {
            return query;
        }
        PageQueryBinder.bind(request, query);
        if (query.getPageNo() == null && request.getPageNum() != null) {
            query.setPageNo(request.getPageNum());
        }
        query.setEncounterId(request.getEncounterId());
        query.setRequestId(request.getRequestId());
        query.setStatusEnum(request.getStatusEnum());
        query.setCategoryEnum(request.getCategoryEnum());
        query.setStartTime(request.getStartTime());
        query.setEndTime(request.getEndTime());
        return query;
    }

    /**
     * 将医嘱执行单读模型分页映射为抽屉列表响应（IF-OPN-03；1-based 页码，B.10.11）。
     *
     * <p>分页字段遵循 PagedResult 规范（B.10.11）：content 承载列表数据，totalCount 承载总条数。</p>
     *
     * @param page 读模型分页
     * @return 抽屉响应
     */
    @Override
    public ListProcedureRecordsForNurseStationResponse toListProcedureRecordsForNurseStationResponse(Page<OrderExecutionSheetQueryModel> page) {
        List<OrderExecutionSheetItemDto> content = new ArrayList<>();
        if (page != null) {
            for (OrderExecutionSheetQueryModel row : page.getContent()) {
                content.add(this.toOrderExecutionSheetItemDto(row));
            }
        }
        return PagedResultBuilder.build(new ListProcedureRecordsForNurseStationResponse(), content, page);
    }

    /**
     * 将净剩可取消执行记录查询模型列表装配为 IF-NUR-03B 响应。
     *
     * @param netRows 域查询净剩 COMPLETED 行；允许 null
     * @return 列表响应
     */
    @Override
    public ListNetRemainingProcedureRecordsForNurseStationResponse toListNetRemainingProcedureRecordsForNurseStationResponse(
        List<ProcedureRecordQueryModel> netRows) {
        List<NetRemainingProcedureRecordItemDto> content = new ArrayList<>();
        if (netRows != null) {
            for (ProcedureRecordQueryModel row : netRows) {
                NetRemainingProcedureRecordItemDto dto = this.toNetRemainingProcedureRecordItemDto(row);
                if (dto != null) {
                    content.add(dto);
                }
            }
        }
        ListNetRemainingProcedureRecordsForNurseStationResponse response =
            new ListNetRemainingProcedureRecordsForNurseStationResponse();
        response.setRecords(content);
        return response;
    }

    /**
     * 构造按就诊装载收费明细命令。
     *
     * @param encounterId 就诊主键；调用方保证非空
     * @return 收费查询命令
     */
    @Override
    public ChargeItemQueryCommand toChargeItemQueryCommand(Long encounterId) {
        ChargeItemQueryCommand command = new ChargeItemQueryCommand();
        command.setEncounterId(encounterId);
        return command;
    }

    /**
     * 构造创建执行记录命令。
     *
     * @param requestId      请求主键
     * @param requestTable   请求表持久化名
     * @param encounterId    就诊主键
     * @param patientId      患者主键
     * @param categoryEnum   执行类目码
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
     * 构造完成执行命令（结果摘要由护士站占位常量填充以满足完成门禁）。
     *
     * @param procedureId 执行记录主键
     * @param operatorId  操作人主键
     * @return 完成命令
     */
    @Override
    public CompleteProcedureRecordCommand toCompleteProcedureRecordCommand(Long procedureId, Long operatorId) {
        CompleteProcedureRecordCommand command = new CompleteProcedureRecordCommand();
        command.setProcedureId(procedureId);
        command.setOperatorId(operatorId);
        command.setResultText(OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_PROCEDURE_RESULT_TEXT);
        command.setReportJson(null);
        return command;
    }

    /**
     * 构造耗材医嘱执行确认命令（单条批次语义，与领域服务一致）。
     *
     * @param deviceRequestId 耗材请求主键
     * @param performerId     执行人
     * @param performOrgId    执行机构
     * @param operatorId      操作人
     * @return 执行确认命令
     */
    @Override
    public ExecuteDeviceOrderCommand toExecuteDeviceOrderCommand(
        Long deviceRequestId,
        Long performerId,
        Long performOrgId,
        Long operatorId) {
        ExecuteDeviceOrderCommand command = new ExecuteDeviceOrderCommand();
        command.setOperatorId(operatorId);
        ExecuteDeviceOrderCommand.ExecuteDeviceOrderItemCommand item = new ExecuteDeviceOrderCommand.ExecuteDeviceOrderItemCommand();
        item.setId(deviceRequestId);
        item.setPerformerId(performerId);
        item.setPerformOrgId(performOrgId);
        command.setItem(item);
        return command;
    }

    /**
     * 构造耗材发放拣配命令（单条批次语义）。
     *
     * @param dispenseId  发放行主键
     * @param preparerId  拣配人
     * @param lotNumber   批号
     * @param traceNo     追溯码
     * @param quantity    数量
     * @param operatorId  操作人
     * @return 拣配命令
     */
    @Override
    public PrepareDeviceDispenseCommand toPrepareDeviceDispenseCommand(
        Long dispenseId,
        Long preparerId,
        String lotNumber,
        String traceNo,
        BigDecimal quantity,
        Long operatorId) {
        PrepareDeviceDispenseCommand command = new PrepareDeviceDispenseCommand();
        command.setOperatorId(operatorId);
        PrepareDeviceDispenseCommand.PrepareDeviceDispenseItemCommand item =
            new PrepareDeviceDispenseCommand.PrepareDeviceDispenseItemCommand();
        item.setId(dispenseId);
        item.setPreparerId(preparerId);
        item.setLotNumber(lotNumber);
        item.setTraceNo(traceNo);
        item.setQuantity(quantity);
        command.setItem(item);
        return command;
    }

    /**
     * 构造批号匹配门控命令（灵活定位键为单发放主键字符串）。
     *
     * @param idListOrRequestKey 发放定位键；调用方保证非空
     * @param operatorId         操作人主键
     * @return 批号匹配命令
     */
    @Override
    public MatchDispenseLotNumberCommand toMatchDispenseLotNumberCommand(String idListOrRequestKey, Long operatorId) {
        MatchDispenseLotNumberCommand command = new MatchDispenseLotNumberCommand();
        command.setIdListOrRequestKey(idListOrRequestKey);
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 构造核对发放命令（单条批次语义）。
     *
     * @param dispenseId  发放行主键
     * @param performerId 发放人
     * @param quantity    发放数量
     * @param operatorId  操作人
     * @return 核对发放命令
     */
    @Override
    public DispenseDeviceCommand toDispenseDeviceCommand(Long dispenseId, Long performerId, BigDecimal quantity, Long operatorId) {
        DispenseDeviceCommand command = new DispenseDeviceCommand();
        command.setOperatorId(operatorId);
        DispenseDeviceCommand.DispenseDeviceItemCommand item = new DispenseDeviceCommand.DispenseDeviceItemCommand();
        item.setId(dispenseId);
        item.setPerformerId(performerId);
        item.setDispenseQuantity(quantity);
        command.setItem(item);
        return command;
    }

    /**
     * 构造取消执行命令。
     *
     * @param procedureId      执行记录主键
     * @param statusReasonText 取消原因
     * @param refundId         被抵消的已完成执行记录主键（{@code cln_procedure.id}）
     * @param operatorId       操作人
     * @return 取消命令
     */
    @Override
    public CancelProcedureRecordCommand toCancelProcedureRecordCommand(
        Long procedureId,
        String statusReasonText,
        Long refundId,
        Long operatorId) {
        CancelProcedureRecordCommand command = new CancelProcedureRecordCommand();
        command.setProcedureId(procedureId);
        command.setStatusReasonText(statusReasonText);
        command.setRefundId(refundId);
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 构造已发放批量回退为待发命令。
     *
     * @param dispenseIds 发放行主键列表
     * @param operatorId  操作人
     * @return 回退命令
     */
    @Override
    public RevertCompletedBatchToPreparationCommand toRevertCompletedBatchToPreparationCommand(
        List<Long> dispenseIds,
        Long operatorId) {
        RevertCompletedBatchToPreparationCommand command = new RevertCompletedBatchToPreparationCommand();
        command.setIdList(dispenseIds);
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 构造撤销耗材医嘱执行确认命令（单条批次语义）。
     *
     * @param deviceRequestId 耗材请求主键
     * @param operatorId      操作人
     * @return 撤销命令
     */
    @Override
    public RevokeDeviceOrderExecutionCommand toRevokeDeviceOrderExecutionCommand(Long deviceRequestId, Long operatorId) {
        RevokeDeviceOrderExecutionCommand command = new RevokeDeviceOrderExecutionCommand();
        command.setOperatorId(operatorId);
        RevokeDeviceOrderExecutionCommand.RevokeDeviceOrderExecutionItemCommand item =
            new RevokeDeviceOrderExecutionCommand.RevokeDeviceOrderExecutionItemCommand();
        item.setId(deviceRequestId);
        command.setItem(item);
        return command;
    }

    /**
     * 构造库存数量增加命令（批量语义）。
     *
     * @param operatorId                 操作人
     * @param inventoryIdToIncreaseQty 库存项主键 → 增加数量
     * @return 增加命令
     */
    @Override
    public IncreaseQuantityCommand toIncreaseQuantityCommand(Long operatorId, Map<Long, BigDecimal> inventoryIdToIncreaseQty) {
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

    /**
     * 单条门诊信息读模型行 → 护士站队列 DTO。
     *
     * <p>实现要点：仅搬运护士站队列卡片所需的精简字段（就诊号、患者姓名、性别、年龄、业务号、就诊时间），
     * 性别码通过 {@link EnumUtil} 转中文展示，出生日期取日期部分（不含时分秒）。</p>
     *
     * @param row 读模型行；允许 null
     * @return DTO；{@code row} 为 null 时返回 null
     */
    /**
     * 单条医嘱执行单读模型行 → 抽屉 DTO。
     *
     * <p>实现要点：搬运执行记录抽屉展示所需的全部字段，状态枚举通过 {@link EnumUtil} 转中文展示；
     * 项目名/操作/操作人展示列由 AppService 读侧 enrich 回填（B.6.2，禁止 HTTP 直透 JSON）。</p>
     *
     * @param row 读模型行；允许 null
     * @return DTO；{@code row} 为 null 时返回 null
     */
    private OrderExecutionSheetItemDto toOrderExecutionSheetItemDto(OrderExecutionSheetQueryModel row) {
        // 防御性空值处理：上游分页可能包含 null 行（如脏数据），直接返回 null 由调用方过滤。
        if (row == null) {
            return null;
        }
        OrderExecutionSheetItemDto dto = new OrderExecutionSheetItemDto();
        // 执行记录主键与医嘱请求锚点（用于定位原始医嘱）
        dto.setProcedureId(row.getProcedureId());
        dto.setRequestId(row.getRequestId());
        dto.setRequestTable(row.getRequestTable());
        // 执行状态：码值与中文展示（如「准备中」「进行中」「已完成」）
        dto.setStatusEnum(row.getStatusEnum());
        EventStatus status = EnumUtil.fromCode(EventStatus.class, row.getStatusEnum());
        if (status != null) {
            dto.setStatusEnumText(status.getDisplay());
        }
        // 执行类目（药品/耗材/诊疗）与就诊上下文
        dto.setCategoryEnum(row.getCategoryEnum());
        dto.setPatientId(row.getPatientId());
        dto.setEncounterId(row.getEncounterId());
        // 时间线：发生时刻、记录时刻、记录人
        dto.setOccurrenceTime(row.getOccurrenceTime());
        dto.setRecordedTime(row.getRecordedTime());
        dto.setRecorderId(row.getRecorderId());
        // 执行结果；项目名/操作/操作人展示列由 AppService enrich 回填（禁止 JSON 解析路径）。
        dto.setResultText(row.getResultText());
        return dto;
    }

    /**
     * 净剩可取消执行记录行 → IF-NUR-03B DTO。
     *
     * @param row 域查询模型；允许 null
     * @return DTO；{@code row} 为 null 时返回 null
     */
    private NetRemainingProcedureRecordItemDto toNetRemainingProcedureRecordItemDto(ProcedureRecordQueryModel row) {
        if (row == null) {
            return null;
        }
        NetRemainingProcedureRecordItemDto dto = new NetRemainingProcedureRecordItemDto();
        dto.setProcedureId(row.getId());
        dto.setRequestId(row.getRequestId());
        dto.setRequestTable(row.getRequestTable());
        dto.setStatusEnum(row.getStatusEnum());
        EventStatus status = EnumUtil.fromCode(EventStatus.class, row.getStatusEnum());
        if (status != null) {
            dto.setStatusEnumText(status.getDisplay());
        }
        dto.setCategoryEnum(row.getCategoryEnum());
        dto.setEncounterId(row.getEncounterId());
        dto.setPatientId(row.getPatientId());
        dto.setOccurrenceTime(row.getOccurrenceTime());
        dto.setRecordedTime(row.getRecordedTime());
        dto.setRecorderId(row.getRecorderId());
        dto.setResultText(row.getResultText());
        return dto;
    }
}
