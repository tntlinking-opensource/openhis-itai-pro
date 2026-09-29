/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.diagnosticreport.service;

import com.openhis.health.domain.pacs.diagnosticreport.entity.PacsDiagnosticReport;
import com.openhis.health.domain.pacs.diagnosticreport.repository.PacsDiagnosticReportRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PacsDiagnosticReportQueryService} 读侧扩展单测。
 */
@ExtendWith(MockitoExtension.class)
class PacsDiagnosticReportQueryServiceSearchTest {

    @Mock
    private PacsDiagnosticReportRepository reportRepository;

    @InjectMocks
    private PacsDiagnosticReportQueryService queryService;

    @Test
    void searchHistoryByPatient_nullPatientId_throwsInvalidArgument() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> queryService.searchHistoryByPatient(null, null, PageRequest.of(0, 10)));
        assertEquals(ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, exception.getErrorCode());
    }

    @Test
    void getById_notFound_throwsReportNotFound() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> queryService.getById(99L));
        assertEquals(ErrorCodeConstants.PACS_REPORT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchReports_withStatus_invokesFindAll() {
        Page<PacsDiagnosticReport> empty =
            new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(reportRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(empty);

        queryService.searchReports("final", null, null, null, null, PageRequest.of(0, 10));

        ArgumentCaptor<Specification<PacsDiagnosticReport>> specCaptor =
            ArgumentCaptor.forClass(Specification.class);
        verify(reportRepository).findAll(specCaptor.capture(), any(Pageable.class));
    }
}
