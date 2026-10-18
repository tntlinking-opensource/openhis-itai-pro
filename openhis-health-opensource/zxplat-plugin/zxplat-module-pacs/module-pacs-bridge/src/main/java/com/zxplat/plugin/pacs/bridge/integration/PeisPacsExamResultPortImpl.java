/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.bridge.integration;

import com.zxplat.plugin.pacs.domain.diagnosticreport.queries.model.PacsDiagnosticReportDetailQueryModel;
import com.zxplat.plugin.pacs.domain.diagnosticreport.service.PacsDiagnosticReportQueryService;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsWorkItemDetailQueryModel;
import com.zxplat.plugin.pacs.domain.workitem.service.PacsWorkItemQueryService;
import com.zxplat.domain.share.extension.pacsintegration.PeisPacsExamResultPort;
import com.zxplat.domain.share.extension.pacsintegration.PeisPacsReportSummaryView;
import com.zxplat.domain.share.extension.pacsintegration.PeisPacsWorkItemView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * PEIS 读取院内 RIS 工单/报告的 SPI 实现（仅 with-pacs 装配）。
 */
@Service
@RequiredArgsConstructor
public class PeisPacsExamResultPortImpl implements PeisPacsExamResultPort {

    private final PacsWorkItemQueryService pacsWorkItemQueryService;
    private final PacsDiagnosticReportQueryService pacsDiagnosticReportQueryService;

    /**
     * 按服务申请反查检查工单。
     *
     * @param serviceRequestId 服务申请主键
     * @return 工单摘要；无关联时 empty
     */
    @Override
    public Optional<PeisPacsWorkItemView> findWorkItemByServiceRequestId(Long serviceRequestId) {
        Optional<PacsWorkItemDetailQueryModel> workItemOpt =
            pacsWorkItemQueryService.findOptionalByServiceRequestId(serviceRequestId);
        if (workItemOpt.isEmpty()) {
            return Optional.empty();
        }
        PeisPacsWorkItemView view = new PeisPacsWorkItemView();
        view.setId(workItemOpt.get().getId());
        return Optional.of(view);
    }

    /**
     * 按工单取主报告。
     *
     * @param workItemId 工单主键
     * @return 报告摘要；无报告时 empty
     */
    @Override
    public Optional<PeisPacsReportSummaryView> findReportByWorkItemId(Long workItemId) {
        Optional<PacsDiagnosticReportDetailQueryModel> reportOpt =
            pacsDiagnosticReportQueryService.findByWorkItemId(workItemId);
        if (reportOpt.isEmpty()) {
            return Optional.empty();
        }
        PacsDiagnosticReportDetailQueryModel report = reportOpt.get();
        PeisPacsReportSummaryView view = new PeisPacsReportSummaryView();
        view.setId(report.getId());
        view.setStatusEnum(report.getStatusEnum());
        view.setConclusion(report.getConclusion());
        view.setFindings(report.getFindings());
        return Optional.of(view);
    }
}
