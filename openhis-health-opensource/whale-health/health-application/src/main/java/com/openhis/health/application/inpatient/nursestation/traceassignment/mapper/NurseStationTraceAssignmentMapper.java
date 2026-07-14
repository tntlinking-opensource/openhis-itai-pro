/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.traceassignment.mapper;

import com.openhis.health.application.inpatient.nursestation.traceassignment.dto.PatientTraceAssignmentGroupDto;
import com.openhis.health.application.inpatient.nursestation.traceassignment.dto.PatientTraceAssignmentQueueRowDto;
import com.openhis.health.application.inpatient.nursestation.traceassignment.request.PagePatientsPendingTraceAssignmentRequest;
import com.openhis.health.application.inpatient.nursestation.traceassignment.response.ListPatientTraceAssignmentGroupsResponse;
import com.openhis.health.application.inpatient.nursestation.traceassignment.response.PagePatientsPendingTraceAssignmentResponse;
import com.openhis.health.application.inpatient.nursestation.traceassignment.response.ResolveTraceCodesForPatientResponse;
import com.openhis.health.application.inpatient.nursestation.traceassignment.response.SavePatientTraceAssignmentResponse;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.medication.medicationdispense.query.EncountersPendingTraceAssignmentPageQuery;
import com.openhis.health.application.inpatient.nursestation.traceassignment.support.ResolveTraceCodesResult;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

/**
 * 住院护士站追溯码分配契约映射（**AS-IP-NUR-015**）；不承载业务流程判断。
 */
public interface NurseStationTraceAssignmentMapper {

    /**
     * 可见就诊查询入参装配（管辖病区 + 关键字）。
     *
     * @param managedWardIds 管辖病区主键
     * @param keyword        可选关键字
     * @return 住院就诊分页查询
     */
    InpatientEncounterPageQuery toInpatientEncounterVisibilityQuery(List<Long> managedWardIds, String keyword);

    /**
     * 待分配患者队列域分页入参。
     *
     * @param request      分页请求
     * @param encounterIds 可见就诊集合
     * @return 域分页查询
     */
    EncountersPendingTraceAssignmentPageQuery toEncountersPendingTraceAssignmentPageQuery(
        PagePatientsPendingTraceAssignmentRequest request,
        List<Long> encounterIds);

    /**
     * IF-IP-TRC-01 分页响应装配。
     *
     * @param page 域分页
     * @param rows 已装配队列行
     * @return 分页响应
     */
    PagePatientsPendingTraceAssignmentResponse toPendingTraceAssignmentPageResponse(
        Page<?> page,
        List<PatientTraceAssignmentQueueRowDto> rows);

    /**
     * IF-IP-TRC-02 汇总组列表响应。
     *
     * @param groups 汇总组 DTO
     * @return 列表响应
     */
    ListPatientTraceAssignmentGroupsResponse toPatientTraceAssignmentGroupsResponse(
        List<PatientTraceAssignmentGroupDto> groups);

    /**
     * IF-IP-TRC-03 追溯码解析预览响应。
     *
     * @param resolveResult 解析结果
     * @return 预览响应
     */
    ResolveTraceCodesForPatientResponse toResolveTraceCodesResponse(ResolveTraceCodesResult resolveResult);

    /**
     * IF-IP-TRC-04 保存响应。
     *
     * @param savedGroupCount 成功组数
     * @return 保存响应
     */
    SavePatientTraceAssignmentResponse toSavePatientTraceAssignmentResponse(Integer savedGroupCount);

    /**
     * 就诊上下文列表 → encounterId 索引。
     *
     * @param contexts 上下文列表
     * @return encounterId → 上下文
     */
    Map<Long, InpatientEncounterContextQueryModel> indexContextsByEncounterId(
        List<InpatientEncounterContextQueryModel> contexts);
}
