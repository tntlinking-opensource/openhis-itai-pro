/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.deptdesk.mapper;

import com.openhis.health.application.peis.deptdesk.dto.CommonResultOptionDto;
import com.openhis.health.application.peis.deptdesk.dto.ObservationRowDto;
import com.openhis.health.application.peis.deptdesk.dto.PendingDeskRowDto;
import com.openhis.health.application.peis.deptdesk.request.SaveObservationRequest;
import com.openhis.health.application.peis.deptdesk.request.SearchPendingRequest;
import com.openhis.health.application.peis.deptdesk.response.ObservationListResponse;
import com.openhis.health.application.peis.deptdesk.response.SaveObservationResponse;
import com.openhis.health.application.peis.deptdesk.response.SearchPendingResponse;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.domain.clinical.observation.command.CreateObservationCommand;
import com.openhis.health.domain.clinical.observation.command.FinalizeObservationCommand;
import com.openhis.health.domain.clinical.observation.command.UpdateObservationCommand;
import com.openhis.health.domain.clinical.observation.entity.Observation;
import com.openhis.health.domain.clinical.observation.queries.model.ObservationModel;
import com.openhis.health.domain.peis.commonresult.queries.model.PeisCommonResultModel;
import com.openhis.health.domain.peis.encounterprogress.queries.model.DeptDeskRowModel;
import com.openhis.health.domain.peis.encounterprogress.queries.query.DeptDeskQuery;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 分科诊台应用层映射实现（AS-PEIS-DES-001）。
 */
@Component
public class PeisDeptDeskMapperImpl implements PeisDeptDeskMapper {

    /**
     * 待检列表请求转领域查询。
     *
     * @param request HTTP 请求
     * @return 领域查询
     */
    @Override
    public DeptDeskQuery toDeptDeskQuery(SearchPendingRequest request) {
        DeptDeskQuery query = new DeptDeskQuery();
        if (request == null) {
            return query;
        }
        PageQueryBinder.bind(request, query);
        query.setDeptOrgId(request.getDeptOrgId());
        // 业务日：父类 LocalDateTime 归一到自然日起止
        LocalDateTime start = request.getStartDate();
        if (start != null) {
            query.setStartDate(start.toLocalDate().atStartOfDay());
        }
        LocalDateTime end = request.getEndDate();
        if (end != null) {
            query.setEndDate(end.toLocalDate().atTime(LocalTime.MAX));
        }
        return query;
    }

    /**
     * 分页待检行转响应。
     *
     * @param page 领域分页
     * @param rows DTO 行
     * @return 分页响应
     */
    @Override
    public SearchPendingResponse toSearchPendingResponse(Page<DeptDeskRowModel> page, List<PendingDeskRowDto> rows) {
        return PagedResultBuilder.build(new SearchPendingResponse(), rows, page);
    }

    /**
     * 领域待检行转 DTO。
     *
     * @param models 领域行
     * @return DTO 列表
     */
    @Override
    public List<PendingDeskRowDto> toPendingDeskRows(List<DeptDeskRowModel> models) {
        List<PendingDeskRowDto> rows = new ArrayList<>();
        if (models == null) {
            return rows;
        }
        for (DeptDeskRowModel model : models) {
            PendingDeskRowDto row = new PendingDeskRowDto();
            row.setEncounterId(model.getEncounterId());
            row.setPatientId(model.getPatientId());
            row.setPatientName(model.getPatientName());
            row.setBusNo(model.getBusNo());
            row.setServiceRequestId(model.getServiceRequestId());
            row.setActivityId(model.getActivityId());
            row.setActivityDisplay(model.getActivityDisplay());
            row.setExecStatusEnum(model.getExecStatusEnum());
            row.setLisDispatchEnum(model.getLisDispatchEnum());
            row.setPatientAge(model.getPatientAge());
            row.setGroupFlag(model.getGroupFlag());
            row.setEncounterStatusEnum(model.getEncounterStatusEnum());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 观察读模型转 DTO（含 OD enrich）。
     *
     * @param models            观察读模型
     * @param definitionDisplay 定义展示名映射
     * @param categoryCode      定义分类映射
     * @return DTO 列表
     */
    @Override
    public List<ObservationRowDto> toObservationRows(
        List<ObservationModel> models,
        Map<Long, String> definitionDisplay,
        Map<Long, String> categoryCode) {
        List<ObservationRowDto> rows = new ArrayList<>();
        if (models == null) {
            return rows;
        }
        for (ObservationModel model : models) {
            ObservationRowDto row = new ObservationRowDto();
            row.setId(model.getId());
            row.setDefinitionId(model.getDefinitionId());
            if (definitionDisplay != null && model.getDefinitionId() != null) {
                row.setDefinitionDisplay(definitionDisplay.get(model.getDefinitionId()));
            }
            if (categoryCode != null && model.getDefinitionId() != null) {
                row.setCategoryCode(categoryCode.get(model.getDefinitionId()));
            }
            row.setValueQuantity(model.getValueQuantity());
            row.setValueString(model.getValueString());
            row.setInterpretationCode(model.getInterpretationCode());
            row.setStatusEnum(model.getStatusEnum());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 常见结果读模型转下拉 DTO。
     *
     * @param models 常见结果
     * @return 下拉项
     */
    @Override
    public List<CommonResultOptionDto> toCommonResultOptions(List<PeisCommonResultModel> models) {
        List<CommonResultOptionDto> options = new ArrayList<>();
        if (models == null) {
            return options;
        }
        for (PeisCommonResultModel model : models) {
            CommonResultOptionDto option = new CommonResultOptionDto();
            option.setId(model.getId());
            option.setDisplay(model.getDisplay());
            option.setValueString(model.getValueString());
            option.setInterpretationCode(model.getInterpretationCode());
            options.add(option);
        }
        return options;
    }

    /**
     * 保存请求转新建命令。
     *
     * @param request        HTTP 请求
     * @param tenantId       租户
     * @param encounterId    就诊
     * @param performerId    执行人
     * @param definitionCode OD 编码
     * @return 新建命令
     */
    @Override
    public CreateObservationCommand toCreateCommand(
        SaveObservationRequest request,
        Long tenantId,
        Long encounterId,
        Long performerId,
        String definitionCode) {
        CreateObservationCommand cmd = new CreateObservationCommand();
        cmd.setTenantId(tenantId);
        cmd.setOrgId(request.getDeptOrgId());
        cmd.setEncounterId(encounterId);
        cmd.setServiceRequestId(request.getServiceRequestId());
        cmd.setDefinitionId(request.getDefinitionId());
        cmd.setCode(definitionCode);
        cmd.setValueQuantity(request.getValueQuantity());
        cmd.setValueString(request.getValueString());
        cmd.setInterpretationCode(request.getInterpretationCode());
        cmd.setPerformerId(performerId);
        return cmd;
    }

    /**
     * 保存请求转更新命令。
     *
     * @param request       HTTP 请求
     * @param observationId 观察实例主键
     * @param performerId   执行人
     * @return 更新命令
     */
    @Override
    public UpdateObservationCommand toUpdateCommand(
        SaveObservationRequest request, Long observationId, Long performerId) {
        UpdateObservationCommand cmd = new UpdateObservationCommand();
        cmd.setObservationId(observationId);
        cmd.setValueQuantity(request.getValueQuantity());
        cmd.setValueString(request.getValueString());
        cmd.setInterpretationCode(request.getInterpretationCode());
        cmd.setPerformerId(performerId);
        return cmd;
    }

    /**
     * 构建确认命令。
     *
     * @param observationId 观察实例主键
     * @param operatorId    操作人
     * @return 确认命令
     */
    @Override
    public FinalizeObservationCommand toFinalizeCommand(Long observationId, Long operatorId) {
        FinalizeObservationCommand cmd = new FinalizeObservationCommand();
        cmd.setObservationId(observationId);
        cmd.setOperatorId(operatorId);
        return cmd;
    }

    /**
     * 实体转保存响应。
     *
     * @param observation 观察实例
     * @return 保存响应
     */
    @Override
    public SaveObservationResponse toSaveResponse(Observation observation) {
        SaveObservationResponse response = new SaveObservationResponse();
        response.setObservationId(observation.getId());
        response.setStatusEnum(observation.getStatusEnum());
        return response;
    }

    /**
     * 组装观察列表响应。
     *
     * @param serviceRequestId 服务申请主键
     * @param rows             DTO 行
     * @return 列表响应
     */
    @Override
    public ObservationListResponse toObservationListResponse(Long serviceRequestId, List<ObservationRowDto> rows) {
        ObservationListResponse response = new ObservationListResponse();
        response.setServiceRequestId(serviceRequestId);
        response.setObservations(rows);
        return response;
    }
}
