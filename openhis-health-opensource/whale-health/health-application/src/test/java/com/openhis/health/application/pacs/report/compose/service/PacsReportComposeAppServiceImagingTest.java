/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.report.compose.service;

import com.openhis.health.application.pacs.report.compose.mapper.PacsReportComposeMapper;
import com.openhis.health.application.pacs.report.compose.request.OpenComposeRequest;
import com.openhis.health.application.pacs.report.compose.response.OpenComposeResponse;
import com.openhis.health.application.pacs.report.shared.ReportImagingAttachmentAssembler;
import com.openhis.health.application.pacs.report.shared.dto.ReportImagingAttachmentDto;
import com.openhis.health.application.pacs.report.shared.dto.ReportKeyImageDto;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.pacs.diagnosticreport.command.OpenOrCreatePacsReportCommand;
import com.openhis.health.domain.pacs.diagnosticreport.entity.PacsDiagnosticReport;
import com.openhis.health.domain.pacs.diagnosticreport.service.PacsDiagnosticReportDomainService;
import com.openhis.health.domain.pacs.reporttemplate.service.PacsReportTemplateDomainService;
import com.openhis.health.domain.pacs.workitem.queries.model.PacsWorkItemDetailQueryModel;
import com.openhis.health.domain.pacs.workitem.service.PacsWorkItemQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PacsReportComposeAppService} 序号 56：报告引用关键图/阅片。
 */
@ExtendWith(MockitoExtension.class)
class PacsReportComposeAppServiceImagingTest {

    @Mock
    private PacsDiagnosticReportDomainService pacsDiagnosticReportDomainService;

    @Mock
    private PacsReportTemplateDomainService pacsReportTemplateDomainService;

    @Mock
    private PacsWorkItemQueryService pacsWorkItemQueryService;

    @Mock
    private PatientQueryService patientQueryService;

    @Mock
    private CurrentPractitionerResolver currentPractitionerResolver;

    @Mock
    private ReportImagingAttachmentAssembler reportImagingAttachmentAssembler;

    private PacsReportComposeAppService appService;

    @BeforeEach
    void setUp() {
        appService = new PacsReportComposeAppService(
            pacsDiagnosticReportDomainService,
            pacsReportTemplateDomainService,
            pacsWorkItemQueryService,
            patientQueryService,
            currentPractitionerResolver,
            new PacsReportComposeMapper(),
            reportImagingAttachmentAssembler);
    }

    @Test
    void openCompose_attachesKeyImagesAndViewerWhenImagingPresent() {
        when(currentPractitionerResolver.requireOperatorId(
            eq(ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT), any())).thenReturn(9L);

        PacsDiagnosticReport report = new PacsDiagnosticReport();
        report.setId(100L);
        report.setWorkItemId(11L);
        report.setPatientId(22L);
        report.setEncounterId(33L);
        report.setStatusEnum("preliminary");
        when(pacsDiagnosticReportDomainService.openOrCreate(any(OpenOrCreatePacsReportCommand.class)))
            .thenReturn(report);

        PacsWorkItemDetailQueryModel workItem = new PacsWorkItemDetailQueryModel();
        workItem.setId(11L);
        workItem.setAccessionNumber("A001");
        workItem.setStatusEnum("completed");
        workItem.setHasMedia(Boolean.TRUE);
        when(pacsWorkItemQueryService.getDetail(11L)).thenReturn(workItem);
        when(patientQueryService.findPatientNamesByIds(List.of(22L))).thenReturn(Map.of(22L, "张三"));

        ReportImagingAttachmentDto imaging = new ReportImagingAttachmentDto();
        imaging.setHasMedia(Boolean.TRUE);
        imaging.setHasStudy(Boolean.TRUE);
        imaging.setStudyInstanceUid("1.2.3");
        imaging.setViewerAvailable(Boolean.TRUE);
        imaging.setViewerLaunchUrl("https://ohif.example/viewer?StudyInstanceUIDs=1.2.3&lng=zh&token=t");
        ReportKeyImageDto key = new ReportKeyImageDto();
        key.setMediaId(501L);
        key.setPreviewUrl("https://minio/key-images/x.jpg");
        imaging.setKeyImages(List.of(key));
        when(reportImagingAttachmentAssembler.assemble(11L, Boolean.TRUE)).thenReturn(imaging);

        OpenComposeRequest request = new OpenComposeRequest();
        request.setWorkItemId(11L);
        OpenComposeResponse response = appService.openCompose(request);

        assertEquals(100L, response.getReportId());
        assertNotNull(response.getImaging());
        assertTrue(response.getImaging().getViewerAvailable());
        assertEquals(1, response.getImaging().getKeyImages().size());
        assertEquals("https://minio/key-images/x.jpg", response.getImaging().getKeyImages().get(0).getPreviewUrl());
        verify(reportImagingAttachmentAssembler).assemble(11L, Boolean.TRUE);
    }

    @Test
    void openCompose_degradesWhenImagingUnavailable() {
        when(currentPractitionerResolver.requireOperatorId(
            eq(ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT), any())).thenReturn(9L);

        PacsDiagnosticReport report = new PacsDiagnosticReport();
        report.setId(100L);
        report.setWorkItemId(11L);
        report.setPatientId(22L);
        report.setStatusEnum("preliminary");
        when(pacsDiagnosticReportDomainService.openOrCreate(any())).thenReturn(report);

        PacsWorkItemDetailQueryModel workItem = new PacsWorkItemDetailQueryModel();
        workItem.setId(11L);
        workItem.setAccessionNumber("A001");
        workItem.setStatusEnum("completed");
        workItem.setHasMedia(Boolean.FALSE);
        when(pacsWorkItemQueryService.getDetail(11L)).thenReturn(workItem);
        when(patientQueryService.findPatientNamesByIds(any())).thenReturn(Collections.emptyMap());

        ReportImagingAttachmentDto empty = new ReportImagingAttachmentDto();
        when(reportImagingAttachmentAssembler.assemble(11L, Boolean.FALSE)).thenReturn(empty);

        OpenComposeRequest request = new OpenComposeRequest();
        request.setWorkItemId(11L);
        OpenComposeResponse response = appService.openCompose(request);

        assertNotNull(response.getImaging());
        assertFalse(Boolean.TRUE.equals(response.getImaging().getViewerAvailable()));
        assertTrue(response.getImaging().getKeyImages() == null
            || response.getImaging().getKeyImages().isEmpty());
    }
}
