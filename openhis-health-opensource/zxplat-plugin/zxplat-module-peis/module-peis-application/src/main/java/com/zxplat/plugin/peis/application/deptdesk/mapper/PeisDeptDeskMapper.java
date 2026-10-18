/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.deptdesk.mapper;

import com.zxplat.plugin.peis.application.deptdesk.dto.CommonResultOptionDto;
import com.zxplat.plugin.peis.application.deptdesk.dto.ObservationRowDto;
import com.zxplat.plugin.peis.application.deptdesk.dto.PendingDeskRowDto;
import com.zxplat.plugin.peis.application.deptdesk.request.SaveObservationRequest;
import com.zxplat.plugin.peis.application.deptdesk.request.SearchPendingRequest;
import com.zxplat.plugin.peis.application.deptdesk.response.ObservationListResponse;
import com.zxplat.plugin.peis.application.deptdesk.response.SaveObservationResponse;
import com.zxplat.plugin.peis.application.deptdesk.response.SearchPendingResponse;
import com.zxplat.domain.clinical.observation.command.CreateObservationCommand;
import com.zxplat.domain.clinical.observation.command.FinalizeObservationCommand;
import com.zxplat.domain.clinical.observation.command.UpdateObservationCommand;
import com.zxplat.domain.clinical.observation.entity.Observation;
import com.zxplat.domain.clinical.observation.queries.model.ObservationModel;
import com.zxplat.plugin.peis.domain.commonresult.queries.model.PeisCommonResultModel;
import com.zxplat.plugin.peis.domain.encounterprogress.queries.model.DeptDeskRowModel;
import com.zxplat.plugin.peis.domain.encounterprogress.queries.query.DeptDeskQuery;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

/**
 * 分科诊台应用层映射（AS-PEIS-DES-001）。
 */
public interface PeisDeptDeskMapper {

    /**
     * 待检列表请求转领域查询。
     *
     * @param request  HTTP 请求
     * @return 领域查询
     */
    DeptDeskQuery toDeptDeskQuery(SearchPendingRequest request);

    /**
     * 分页待检行转响应。
     *
     * @param page  领域分页
     * @param rows  DTO 行
     * @return 分页响应
     */
    SearchPendingResponse toSearchPendingResponse(Page<DeptDeskRowModel> page, List<PendingDeskRowDto> rows);

    /**
     * 领域待检行转 DTO。
     *
     * @param models 领域行
     * @return DTO 列表
     */
    List<PendingDeskRowDto> toPendingDeskRows(List<DeptDeskRowModel> models);

    /**
     * 观察读模型转 DTO（含 OD enrich）。
     *
     * @param models            观察读模型
     * @param definitionDisplay 定义展示名映射
     * @param categoryCode      定义分类映射
     * @return DTO 列表
     */
    List<ObservationRowDto> toObservationRows(
        List<ObservationModel> models,
        Map<Long, String> definitionDisplay,
        Map<Long, String> categoryCode);

    /**
     * 常见结果读模型转下拉 DTO。
     *
     * @param models 常见结果
     * @return 下拉项
     */
    List<CommonResultOptionDto> toCommonResultOptions(List<PeisCommonResultModel> models);

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
    CreateObservationCommand toCreateCommand(
        SaveObservationRequest request,
        Long tenantId,
        Long encounterId,
        Long performerId,
        String definitionCode);

    /**
     * 保存请求转更新命令。
     *
     * @param request       HTTP 请求
     * @param observationId 观察实例主键
     * @param performerId   执行人
     * @return 更新命令
     */
    UpdateObservationCommand toUpdateCommand(
        SaveObservationRequest request, Long observationId, Long performerId);

    /**
     * 构建确认命令。
     *
     * @param observationId 观察实例主键
     * @param operatorId    操作人
     * @return 确认命令
     */
    FinalizeObservationCommand toFinalizeCommand(Long observationId, Long operatorId);

    /**
     * 实体转保存响应。
     *
     * @param observation 观察实例
     * @return 保存响应
     */
    SaveObservationResponse toSaveResponse(Observation observation);

    /**
     * 组装观察列表响应。
     *
     * @param serviceRequestId 服务申请主键
     * @param rows               DTO 行
     * @return 列表响应
     */
    ObservationListResponse toObservationListResponse(Long serviceRequestId, List<ObservationRowDto> rows);
}
