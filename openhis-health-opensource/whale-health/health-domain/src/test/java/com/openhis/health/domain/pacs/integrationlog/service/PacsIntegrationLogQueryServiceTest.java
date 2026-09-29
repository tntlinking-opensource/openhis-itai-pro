/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.integrationlog.service;

import com.openhis.health.domain.pacs.integrationlog.entity.PacsIntegrationLog;
import com.openhis.health.domain.pacs.integrationlog.repository.PacsIntegrationLogRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * {@link PacsIntegrationLogQueryService} 读侧单测。
 */
@ExtendWith(MockitoExtension.class)
class PacsIntegrationLogQueryServiceTest {

    @Mock
    private PacsIntegrationLogRepository repository;

    @InjectMocks
    private PacsIntegrationLogQueryService queryService;

    @Test
    void getById_notFound_throwsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> queryService.getById(99L));
        assertEquals(ErrorCodeConstants.PACS_INTEGRATION_LOG_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_invokesFindAll() {
        Page<PacsIntegrationLog> empty =
            new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(empty);

        queryService.search(null, null, null, null, null, null, null, PageRequest.of(0, 10));

        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }
}
