/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.workitem.service;

import com.openhis.health.domain.pacs.workitem.queries.PacsWorkItemSearchQuery;
import com.openhis.health.domain.pacs.workitem.repository.PacsWorkItemOrderRepository;
import com.openhis.health.domain.pacs.workitem.repository.PacsWorkItemRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PacsWorkItemQueryService} EXE-03/04 读侧单测。
 */
@ExtendWith(MockitoExtension.class)
class PacsWorkItemQueryServiceExeExtensionTest {

    @Mock
    private PacsWorkItemRepository workItemRepository;

    @Mock
    private PacsWorkItemOrderRepository orderRepository;

    @InjectMocks
    private PacsWorkItemQueryService queryService;

    @Test
    void searchCompletedOtherDept_missingExcludeOrg_throws() {
        PacsWorkItemSearchQuery query = new PacsWorkItemSearchQuery();
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> queryService.searchCompletedOtherDept(query, PageRequest.of(0, 10)));
        assertEquals(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, exception.getErrorCode());
    }

    @Test
    void searchEnteredInError_invokesRepository() {
        when(workItemRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(Collections.emptyList()));

        queryService.searchEnteredInError(new PacsWorkItemSearchQuery(), PageRequest.of(0, 10));

        verify(workItemRepository).findAll(any(Specification.class), any(PageRequest.class));
    }

    @Test
    void searchCompletedOtherDept_happyPath_invokesRepository() {
        PacsWorkItemSearchQuery query = new PacsWorkItemSearchQuery();
        query.setExcludeOrganizationId(100L);
        when(workItemRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(Collections.emptyList()));

        queryService.searchCompletedOtherDept(query, PageRequest.of(0, 10));

        verify(workItemRepository).findAll(any(Specification.class), any(PageRequest.class));
    }
}
