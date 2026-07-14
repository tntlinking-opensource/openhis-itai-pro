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
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.PractitionerWorkloadConstants;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * {@link PractitionerWorkloadMapper} 实现（AS-RPT-001 IF-RPT-WKL-01～04）。
 */
@Component
public class PractitionerWorkloadMapperImpl implements PractitionerWorkloadMapper {

    /**
     * 汇总查询 Request → 领域读查询。
     *
     * @param request IF-RPT-WKL-02 请求；调用方保证非空
     * @return 领域查询；不含分页键
     */
    @Override
    public PractitionerWorkloadPageQuery toPractitionerWorkloadPageQuery(QueryPractitionerWorkloadSummaryRequest request) {
        PractitionerWorkloadPageQuery query = new PractitionerWorkloadPageQuery();
        this.applyCommonFilter(query, request.getEncounterScope(), request.getDateFrom(), request.getDateTo(),
            request.getOrganizationId(), request.getPractitionerId());
        return query;
    }

    /**
     * 明细分页 Request → 领域读查询。
     *
     * @param request IF-RPT-WKL-03 请求；调用方保证非空
     * @return 含 pageNo/pageSize 的领域查询
     */
    @Override
    public PractitionerWorkloadPageQuery toPractitionerWorkloadPageQuery(PagePractitionerWorkloadDetailRequest request) {
        PractitionerWorkloadPageQuery query = new PractitionerWorkloadPageQuery();
        PageQueryBinder.bind(request, query);
        this.applyCommonFilter(query, request.getEncounterScope(), request.getDateFrom(), request.getDateTo(),
            request.getOrganizationId(), request.getPractitionerId());
        return query;
    }

    /**
     * 导出 Request → 领域读查询。
     *
     * @param request IF-RPT-WKL-04 请求；调用方保证非空
     * @return 领域查询；pageSize 由调用方设为导出步长
     */
    @Override
    public PractitionerWorkloadPageQuery toPractitionerWorkloadPageQuery(ExportPractitionerWorkloadDetailRequest request) {
        PractitionerWorkloadPageQuery query = new PractitionerWorkloadPageQuery();
        this.applyCommonFilter(query, request.getEncounterScope(), request.getDateFrom(), request.getDateTo(),
            request.getOrganizationId(), request.getPractitionerId());
        return query;
    }

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
    @Override
    public LoadPractitionerWorkloadPageInitResponse toLoadPractitionerWorkloadPageInitResponse(
        List<OptionDto> encounterScopeOptions,
        List<OptionDto> organizationOptions,
        List<OptionDto> practitionerOptions,
        String defaultDateFrom,
        String defaultDateTo) {
        LoadPractitionerWorkloadPageInitResponse response = new LoadPractitionerWorkloadPageInitResponse();
        response.setEncounterScopeOptions(encounterScopeOptions);
        response.setDefaultEncounterScope(PractitionerWorkloadConstants.ENCOUNTER_SCOPE_OUTPATIENT);
        response.setOrganizationOptions(organizationOptions);
        response.setPractitionerOptions(practitionerOptions);
        response.setDefaultDateFrom(defaultDateFrom);
        response.setDefaultDateTo(defaultDateTo);
        response.setDefaultActiveTab(PractitionerWorkloadConstants.DEFAULT_ACTIVE_TAB_SUMMARY);
        return response;
    }

    /**
     * 汇总装配 → 汇总响应。
     *
     * @param encounterScope     回显就诊范围
     * @param organizationId       已选科室 id；未选为 null
     * @param organizationName     已选科室名；未选为 null
     * @param finTypeRows          fin_type 行
     * @param registrationRows     号源行
     * @param summaryTotals        页脚合计
     * @return 汇总响应
     */
    @Override
    public QueryPractitionerWorkloadSummaryResponse toQueryPractitionerWorkloadSummaryResponse(
        String encounterScope,
        Long organizationId,
        String organizationName,
        List<PractitionerWorkloadFinTypeRowDto> finTypeRows,
        List<PractitionerWorkloadRegistrationRowDto> registrationRows,
        PractitionerWorkloadSummaryTotalsDto summaryTotals) {
        QueryPractitionerWorkloadSummaryResponse response = new QueryPractitionerWorkloadSummaryResponse();
        response.setEncounterScope(encounterScope);
        response.setOrganizationId(organizationId);
        response.setOrganizationName(organizationName);
        response.setFinTypeRows(finTypeRows);
        response.setRegistrationRows(registrationRows);
        response.setSummaryTotals(summaryTotals);
        return response;
    }

    /**
     * 明细分页 → 分页响应。
     *
     * @param content       明细行
     * @param totalCount    总条数
     * @param pageNo        当前页
     * @param pageSize      页大小
     * @param summaryTotals 可选 digest
     * @return 分页响应
     */
    @Override
    public PagePractitionerWorkloadDetailResponse toPagePractitionerWorkloadDetailResponse(
        List<PractitionerWorkloadDetailLineDto> content,
        long totalCount,
        int pageNo,
        int pageSize,
        PractitionerWorkloadSummaryTotalsDto summaryTotals) {
        PagePractitionerWorkloadDetailResponse response = new PagePractitionerWorkloadDetailResponse();
        response.setContent(content);
        response.setTotalCount(totalCount);
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        response.setSummaryTotals(summaryTotals);
        return response;
    }

    /**
     * 明细行 → 导出行。
     *
     * @param line 明细行；调用方保证非空
     * @return 导出行 DTO
     */
    @Override
    public PractitionerWorkloadExportRowDto toExportRowDto(PractitionerWorkloadDetailLineDto line) {
        if (line == null) {
            return null;
        }
        PractitionerWorkloadExportRowDto row = new PractitionerWorkloadExportRowDto();
        row.setRequestingOrgName(line.getRequestingOrgName());
        row.setCatalogTypeDisplay(line.getCatalogTypeDisplay());
        row.setProductName(line.getProductName());
        row.setProductCode(line.getProductCode());
        row.setSpecification(line.getSpecification());
        row.setPractitionerName(line.getPractitionerName());
        row.setEnteredDate(line.getEnteredDate());
        row.setReceivableAmount(line.getReceivableAmount());
        row.setActualReceivedAmount(line.getActualReceivedAmount());
        row.setCostAmount(line.getCostAmount());
        return row;
    }

    /**
     * 导出字节 → 导出响应。
     *
     * @param fileName  文件名
     * @param body      xlsx 字节
     * @param rowCount  行数
     * @param truncated 是否触顶
     * @return 导出响应
     */
    @Override
    public ExportPractitionerWorkloadDetailResponse toExportPractitionerWorkloadDetailResponse(
        String fileName,
        byte[] body,
        int rowCount,
        boolean truncated) {
        ExportPractitionerWorkloadDetailResponse response = new ExportPractitionerWorkloadDetailResponse();
        response.setFileName(fileName);
        response.setContentType(PractitionerWorkloadConstants.EXPORT_CONTENT_TYPE);
        response.setBody(body);
        response.setRowCount(rowCount);
        response.setTruncated(truncated);
        return response;
    }

    /**
     * 工作量报表：写入公共分页筛选（就诊范围、账单日区间、机构、医务人员）。
     *
     * @param query           待填充查询对象
     * @param encounterScope  就诊范围码
     * @param dateFrom        起始展示日期 yyyy-MM-dd
     * @param dateTo          截止展示日期 yyyy-MM-dd
     * @param organizationId  机构 id
     * @param practitionerId  医务人员 id
     */
    private void applyCommonFilter(
        PractitionerWorkloadPageQuery query,
        String encounterScope,
        String dateFrom,
        String dateTo,
        Long organizationId,
        Long practitionerId) {
        query.setEncounterScope(trimToNull(encounterScope));
        query.setBillDateFrom(this.parseDisplayDate(dateFrom));
        query.setBillDateTo(this.parseDisplayDate(dateTo));
        query.setOrganizationId(organizationId);
        query.setPractitionerId(practitionerId);
    }

    /**
     * 展示日期字符串 → {@link LocalDate}；空串返回 null。
     *
     * @param dateText yyyy-MM-dd
     * @return 解析结果
     */
    private LocalDate parseDisplayDate(String dateText) {
        if (StringUtils.isEmpty(dateText)) {
            return null;
        }
        DateTimeFormatter formatter = GeneralSharedConstants.DATE_DISPLAY_FORMATTER;
        return LocalDate.parse(dateText.trim(), formatter);
    }
}
