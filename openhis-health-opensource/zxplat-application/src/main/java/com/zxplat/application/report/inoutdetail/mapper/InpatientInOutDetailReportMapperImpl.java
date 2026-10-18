/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.inoutdetail.mapper;

import com.zxplat.application.report.inoutdetail.dto.InpatientInOutDetailFooterDto;
import com.zxplat.application.report.inoutdetail.dto.InpatientInOutDetailRowDto;
import com.zxplat.application.report.inoutdetail.request.ExportInpatientInOutDetailReportRequest;
import com.zxplat.application.report.inoutdetail.request.PageInpatientInOutDetailReportRequest;
import com.zxplat.application.report.inoutdetail.response.ExportInpatientInOutDetailReportResponse;
import com.zxplat.application.report.inoutdetail.response.ListInpatientInOutDetailWardOptionsResponse;
import com.zxplat.application.report.inoutdetail.response.LoadInpatientInOutDetailReportPageInitResponse;
import com.zxplat.application.report.inoutdetail.response.PageInpatientInOutDetailReportResponse;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.domain.administration.encounter.queries.InpatientInOutDetailReportPageQuery;
import com.zxplat.domain.administration.encounter.queries.model.InpatientInOutDetailPersonTimesQueryModel;
import com.zxplat.framework.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 住院患者入出明细报表 Mapper 实现（AS-RPT-010）。
 *
 * <p>仅字段搬运；scope 预解析与 enrich 在 Support/Enricher。</p>
 */
@Component
public class InpatientInOutDetailReportMapperImpl implements InpatientInOutDetailReportMapper {

    /**
     * 明细分页请求 → 领域分页 Query（不含病区 scope 预解析）。
     *
     * @param request 分页请求；调用方保证非空
     * @return 领域 Query
     */
    @Override
    public InpatientInOutDetailReportPageQuery toPageQuery(PageInpatientInOutDetailReportRequest request) {
        InpatientInOutDetailReportPageQuery query = new InpatientInOutDetailReportPageQuery();
        // 空请求：返回空壳，由 Gate 保证非空
        if (request == null) {
            return query;
        }
        query.setOrganizationId(request.getOrganizationId());
        query.setWardLocationId(request.getWardLocationId());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        return query;
    }

    /**
     * 导出请求 → 领域 Query（不含分页；scope 由 Support 写入）。
     *
     * @param request 导出请求；调用方保证非空
     * @return 领域 Query
     */
    @Override
    public InpatientInOutDetailReportPageQuery toExportQuery(ExportInpatientInOutDetailReportRequest request) {
        InpatientInOutDetailReportPageQuery query = new InpatientInOutDetailReportPageQuery();
        // 空请求：返回空壳
        if (request == null) {
            return query;
        }
        query.setOrganizationId(request.getOrganizationId());
        query.setWardLocationId(request.getWardLocationId());
        return query;
    }

    /**
     * 页初始化主数据 → 响应。
     *
     * @param defaultDateFrom 默认日起
     * @param defaultDateTo 默认日止
     * @param defaultPageSize 默认页大小
     * @param defaultMovementStatus 默认入出状态
     * @param movementStatusOptions 入出状态下拉
     * @param organizationOptions 科室下拉
     * @param wardOptions 病区下拉（初始应为空）
     * @return 初始化响应
     */
    @Override
    public LoadInpatientInOutDetailReportPageInitResponse toPageInitResponse(
        LocalDateTime defaultDateFrom,
        LocalDateTime defaultDateTo,
        int defaultPageSize,
        String defaultMovementStatus,
        List<OptionDto> movementStatusOptions,
        List<OptionDto> organizationOptions,
        List<OptionDto> wardOptions) {
        LoadInpatientInOutDetailReportPageInitResponse response =
            new LoadInpatientInOutDetailReportPageInitResponse();
        response.setDefaultDateFrom(defaultDateFrom);
        response.setDefaultDateTo(defaultDateTo);
        response.setDefaultPageSize(defaultPageSize);
        response.setDefaultMovementStatus(defaultMovementStatus);
        // 下拉空则落空列表，避免前端 NPE
        if (movementStatusOptions == null) {
            response.setMovementStatusOptions(new ArrayList<>());
        } else {
            response.setMovementStatusOptions(movementStatusOptions);
        }
        if (organizationOptions == null) {
            response.setOrganizationOptions(new ArrayList<>());
        } else {
            response.setOrganizationOptions(organizationOptions);
        }
        if (wardOptions == null) {
            response.setWardOptions(new ArrayList<>());
        } else {
            response.setWardOptions(wardOptions);
        }
        return response;
    }

    /**
     * 病区选项 → 响应。
     *
     * @param wardOptions 病区下拉；可空
     * @return 病区选项响应
     */
    @Override
    public ListInpatientInOutDetailWardOptionsResponse toWardOptionsResponse(List<OptionDto> wardOptions) {
        ListInpatientInOutDetailWardOptionsResponse response =
            new ListInpatientInOutDetailWardOptionsResponse();
        // 空入参落空列表，避免前端 NPE
        if (wardOptions == null) {
            response.setWardOptions(new ArrayList<>());
        } else {
            response.setWardOptions(wardOptions);
        }
        return response;
    }

    /**
     * 人次领域模型 → 页脚 DTO。
     *
     * @param personTimes 领域人次；可空
     * @return 页脚
     */
    @Override
    public InpatientInOutDetailFooterDto toFooterDto(InpatientInOutDetailPersonTimesQueryModel personTimes) {
        InpatientInOutDetailFooterDto footer = new InpatientInOutDetailFooterDto();
        // 空模型：人次置 0
        if (personTimes == null) {
            footer.setAdmissionPersonTimes(0L);
            footer.setDischargePersonTimes(0L);
            return footer;
        }
        Long admission = personTimes.getAdmissionPersonTimes();
        Long discharge = personTimes.getDischargePersonTimes();
        // 入院人次空 → 0
        if (admission == null) {
            footer.setAdmissionPersonTimes(0L);
        } else {
            footer.setAdmissionPersonTimes(admission);
        }
        // 出院人次空 → 0
        if (discharge == null) {
            footer.setDischargePersonTimes(0L);
        } else {
            footer.setDischargePersonTimes(discharge);
        }
        return footer;
    }

    /**
     * 明细展示行 + Spring Page + 页脚 + 转科列开关 → 分页响应。
     *
     * @param rows 当前页展示行
     * @param page 领域分页壳
     * @param footer 人次页脚
     * @param showTransferColumn 是否展示转科列
     * @return 分页响应
     */
    @Override
    public PageInpatientInOutDetailReportResponse toPageResponse(
        List<InpatientInOutDetailRowDto> rows,
        Page<?> page,
        InpatientInOutDetailFooterDto footer,
        boolean showTransferColumn) {
        PageInpatientInOutDetailReportResponse response =
            PagedResultBuilder.build(new PageInpatientInOutDetailReportResponse(), rows, page);
        response.setFooter(footer);
        response.setShowTransferColumn(showTransferColumn);
        return response;
    }

    /**
     * 导出二进制 → 导出响应。
     *
     * @param fileName 文件名
     * @param body 字节
     * @param rowCount 行数
     * @param truncated 是否截断
     * @param contentType MIME
     * @param showTransferColumn 是否含转科信息
     * @return 导出响应
     */
    @Override
    public ExportInpatientInOutDetailReportResponse toExportResponse(
        String fileName,
        byte[] body,
        int rowCount,
        boolean truncated,
        String contentType,
        boolean showTransferColumn) {
        ExportInpatientInOutDetailReportResponse response = new ExportInpatientInOutDetailReportResponse();
        response.setFileName(fileName);
        response.setBody(body);
        response.setRowCount(rowCount);
        response.setTruncated(truncated);
        response.setContentType(contentType);
        response.setShowTransferColumn(showTransferColumn);
        return response;
    }
}
