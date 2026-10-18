/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.traceassignment.mapper;

import com.zxplat.application.inpatient.nursestation.traceassignment.dto.PatientTraceAssignmentGroupDto;
import com.zxplat.application.inpatient.nursestation.traceassignment.dto.PatientTraceAssignmentQueueRowDto;
import com.zxplat.application.inpatient.nursestation.traceassignment.dto.TraceCodeMatchPreviewDto;
import com.zxplat.application.inpatient.nursestation.traceassignment.dto.TraceCodeResolveFailItemDto;
import com.zxplat.application.inpatient.nursestation.traceassignment.request.PagePatientsPendingTraceAssignmentRequest;
import com.zxplat.application.inpatient.nursestation.traceassignment.response.ListPatientTraceAssignmentGroupsResponse;
import com.zxplat.application.inpatient.nursestation.traceassignment.response.PagePatientsPendingTraceAssignmentResponse;
import com.zxplat.application.inpatient.nursestation.traceassignment.response.ResolveTraceCodesForPatientResponse;
import com.zxplat.application.inpatient.nursestation.traceassignment.response.SavePatientTraceAssignmentResponse;
import com.zxplat.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.domain.medication.medicationdispense.query.EncountersPendingTraceAssignmentPageQuery;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.inpatient.nursestation.traceassignment.support.ResolveTraceCodesResult;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * {@link NurseStationTraceAssignmentMapper} 默认实现（**AS-IP-NUR-015**）。
 */
@Component
public class NurseStationTraceAssignmentMapperImpl implements NurseStationTraceAssignmentMapper {

    /**
     * 可见就诊查询入参装配（管辖病区 + 关键字）。
     *
     * @param managedWardIds 管辖病区主键
     * @param keyword        可选关键字
     * @return 住院就诊分页查询
     */
    @Override
    public InpatientEncounterPageQuery toInpatientEncounterVisibilityQuery(List<Long> managedWardIds, String keyword) {
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        query.setManagedWardIds(managedWardIds);
        query.setKeyword(trimToNull(keyword));
        query.setNurseStationQueue(Boolean.TRUE);
        return query;
    }

    /**
     * 待分配患者队列域分页入参。
     *
     * @param request      分页请求
     * @param encounterIds 可见就诊集合
     * @return 域分页查询
     */
    @Override
    public EncountersPendingTraceAssignmentPageQuery toEncountersPendingTraceAssignmentPageQuery(
        PagePatientsPendingTraceAssignmentRequest request,
        List<Long> encounterIds) {
        EncountersPendingTraceAssignmentPageQuery query = new EncountersPendingTraceAssignmentPageQuery();
        if (request != null) {
            query.setPageNo(request.getPageNo());
            query.setPageSize(request.getPageSize());
        }
        query.setEncounterIds(encounterIds);
        return query;
    }

    /**
     * IF-IP-TRC-01 分页响应装配。
     *
     * @param page 域分页
     * @param rows 已装配队列行
     * @return 分页响应
     */
    @Override
    public PagePatientsPendingTraceAssignmentResponse toPendingTraceAssignmentPageResponse(
        Page<?> page,
        List<PatientTraceAssignmentQueueRowDto> rows) {
        List<PatientTraceAssignmentQueueRowDto> safeRows = rows;
        if (safeRows == null) {
            safeRows = new ArrayList<>();
        }
        return PagedResultBuilder.build(new PagePatientsPendingTraceAssignmentResponse(), safeRows, page);
    }

    /**
     * IF-IP-TRC-02 汇总组列表响应。
     *
     * @param groups 汇总组 DTO
     * @return 列表响应
     */
    @Override
    public ListPatientTraceAssignmentGroupsResponse toPatientTraceAssignmentGroupsResponse(
        List<PatientTraceAssignmentGroupDto> groups) {
        ListPatientTraceAssignmentGroupsResponse response = new ListPatientTraceAssignmentGroupsResponse();
        List<PatientTraceAssignmentGroupDto> safeGroups = groups;
        if (safeGroups == null) {
            safeGroups = new ArrayList<>();
        }
        response.setGroups(safeGroups);
        return response;
    }

    /**
     * IF-IP-TRC-03 追溯码解析预览响应。
     *
     * @param resolveResult 解析结果
     * @return 预览响应；resolveResult 为 null 时返回空列表
     */
    @Override
    public ResolveTraceCodesForPatientResponse toResolveTraceCodesResponse(ResolveTraceCodesResult resolveResult) {
        ResolveTraceCodesForPatientResponse response = new ResolveTraceCodesForPatientResponse();
        response.setMatches(new ArrayList<>());
        response.setFailItems(new ArrayList<>());
        if (resolveResult == null) {
            return response;
        }
        if (resolveResult.getMatches() != null) {
            for (ResolveTraceCodesResult.GroupMatch match : resolveResult.getMatches()) {
                if (match == null) {
                    continue;
                }
                TraceCodeMatchPreviewDto dto = new TraceCodeMatchPreviewDto();
                dto.setGroupKey(match.getGroupKey());
                dto.setMatchedTraceCodes(match.getMatchedTraceCodes());
                dto.setMedicationId(match.getMedicationId());
                dto.setLotNumber(match.getLotNumber());
                response.getMatches().add(dto);
            }
        }
        if (resolveResult.getFailItems() != null) {
            for (ResolveTraceCodesResult.FailItem failItem : resolveResult.getFailItems()) {
                if (failItem == null) {
                    continue;
                }
                TraceCodeResolveFailItemDto dto = new TraceCodeResolveFailItemDto();
                dto.setTraceCode(failItem.getTraceCode());
                dto.setReasonCode(failItem.getReasonCode());
                response.getFailItems().add(dto);
            }
        }
        return response;
    }

    /**
     * IF-IP-TRC-04 保存响应。
     *
     * @param savedGroupCount 成功组数
     * @return 保存响应
     */
    @Override
    public SavePatientTraceAssignmentResponse toSavePatientTraceAssignmentResponse(Integer savedGroupCount) {
        SavePatientTraceAssignmentResponse response = new SavePatientTraceAssignmentResponse();
        int count = 0;
        if (savedGroupCount != null) {
            count = savedGroupCount.intValue();
        }
        response.setSavedGroupCount(count);
        return response;
    }

    /**
     * 就诊上下文列表 → encounterId 索引。
     *
     * @param contexts 上下文列表
     * @return encounterId → 上下文
     */
    @Override
    public Map<Long, InpatientEncounterContextQueryModel> indexContextsByEncounterId(
        List<InpatientEncounterContextQueryModel> contexts) {
        Map<Long, InpatientEncounterContextQueryModel> index = new HashMap<>();
        if (contexts == null) {
            return index;
        }
        for (InpatientEncounterContextQueryModel context : contexts) {
            if (context == null || context.getEncounterId() == null) {
                continue;
            }
            index.put(context.getEncounterId(), context);
        }
        return index;
    }
}
