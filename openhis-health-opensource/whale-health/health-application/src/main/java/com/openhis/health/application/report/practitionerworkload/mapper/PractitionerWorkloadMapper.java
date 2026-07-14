/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.practitionerworkload.mapper;

import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadDetailLineDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadExportRowDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadFinTypeRowDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadRegistrationRowDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadSummaryTotalsDto;
import com.openhis.health.application.report.practitionerworkload.request.ExportPractitionerWorkloadDetailRequest;
import com.openhis.health.application.report.practitionerworkload.request.PagePractitionerWorkloadDetailRequest;
import com.openhis.health.application.report.practitionerworkload.request.QueryPractitionerWorkloadSummaryRequest;
import com.openhis.health.application.report.practitionerworkload.response.ExportPractitionerWorkloadDetailResponse;
import com.openhis.health.application.report.practitionerworkload.response.LoadPractitionerWorkloadPageInitResponse;
import com.openhis.health.application.report.practitionerworkload.response.PagePractitionerWorkloadDetailResponse;
import com.openhis.health.application.report.practitionerworkload.response.QueryPractitionerWorkloadSummaryResponse;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadPageQuery;
import com.openhis.whale.core.application.dto.OptionDto;

import java.util.List;

/**
 * 科室/医生工作量 Request/Response 与领域 Query 映射（AS-RPT-001 IF-RPT-WKL-01～04）。
 *
 * <p>仅字段搬运，禁止承载业务流程判断。</p>
 */
public interface PractitionerWorkloadMapper {

    /**
     * 汇总查询 Request → 领域读查询。
     *
     * @param request IF-RPT-WKL-02 请求；调用方保证非空
     * @return 领域查询；不含分页键
     */
    PractitionerWorkloadPageQuery toPractitionerWorkloadPageQuery(QueryPractitionerWorkloadSummaryRequest request);

    /**
     * 明细分页 Request → 领域读查询。
     *
     * @param request IF-RPT-WKL-03 请求；调用方保证非空
     * @return 含 pageNo/pageSize 的领域查询
     */
    PractitionerWorkloadPageQuery toPractitionerWorkloadPageQuery(PagePractitionerWorkloadDetailRequest request);

    /**
     * 导出 Request → 领域读查询。
     *
     * @param request IF-RPT-WKL-04 请求；调用方保证非空
     * @return 领域查询；pageSize 由调用方设为导出步长
     */
    PractitionerWorkloadPageQuery toPractitionerWorkloadPageQuery(ExportPractitionerWorkloadDetailRequest request);

    /**
     * 页初始化主数据 → 初始化响应。
     *
     * @param encounterScopeOptions  就诊范围选项
     * @param organizationOptions    科室选项
     * @param practitionerOptions    医生选项
     * @param defaultDateFrom        默认日起
     * @param defaultDateTo          默认日止
     * @return 初始化响应
     */
    LoadPractitionerWorkloadPageInitResponse toLoadPractitionerWorkloadPageInitResponse(
        List<OptionDto> encounterScopeOptions,
        List<OptionDto> organizationOptions,
        List<OptionDto> practitionerOptions,
        String defaultDateFrom,
        String defaultDateTo);

    /**
     * 汇总装配 → 汇总响应。
     *
     * @param encounterScope    回显就诊范围
     * @param organizationId    已选科室 id；未选为 null
     * @param organizationName  已选科室名；未选为 null
     * @param finTypeRows       fin_type 行
     * @param registrationRows  号源行
     * @param summaryTotals     页脚合计
     * @return 汇总响应
     */
    QueryPractitionerWorkloadSummaryResponse toQueryPractitionerWorkloadSummaryResponse(
        String encounterScope,
        Long organizationId,
        String organizationName,
        List<PractitionerWorkloadFinTypeRowDto> finTypeRows,
        List<PractitionerWorkloadRegistrationRowDto> registrationRows,
        PractitionerWorkloadSummaryTotalsDto summaryTotals);

    /**
     * 明细分页 → 分页响应。
     *
     * @param content      明细行
     * @param totalCount   总条数
     * @param pageNo       当前页
     * @param pageSize     页大小
     * @param summaryTotals 可选 digest
     * @return 分页响应
     */
    PagePractitionerWorkloadDetailResponse toPagePractitionerWorkloadDetailResponse(
        List<PractitionerWorkloadDetailLineDto> content,
        long totalCount,
        int pageNo,
        int pageSize,
        PractitionerWorkloadSummaryTotalsDto summaryTotals);

    /**
     * 明细行 → 导出行。
     *
     * @param line 明细行；调用方保证非空
     * @return 导出行 DTO
     */
    PractitionerWorkloadExportRowDto toExportRowDto(PractitionerWorkloadDetailLineDto line);

    /**
     * 导出字节 → 导出响应。
     *
     * @param fileName   文件名
     * @param body       xlsx 字节
     * @param rowCount   行数
     * @param truncated  是否触顶
     * @return 导出响应
     */
    ExportPractitionerWorkloadDetailResponse toExportPractitionerWorkloadDetailResponse(
        String fileName,
        byte[] body,
        int rowCount,
        boolean truncated);
}
