/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.workitem.service;

import com.openhis.health.domain.pacs.workitem.entity.PacsModalityCapability;
import com.openhis.health.domain.pacs.workitem.repository.PacsModalityCapabilityRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ModalityCapabilityQueryService} 分页 search 单测。
 */
@ExtendWith(MockitoExtension.class)
class ModalityCapabilityQueryServiceSearchTest {

    @Mock
    private PacsModalityCapabilityRepository modalityCapabilityRepository;

    @InjectMocks
    private ModalityCapabilityQueryService queryService;

    @Test
    @SuppressWarnings("unchecked")
    void search_invokesFindAll() {
        Page<PacsModalityCapability> empty =
            new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(modalityCapabilityRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(empty);

        queryService.search(null, null, PageRequest.of(0, 10));

        verify(modalityCapabilityRepository).findAll(any(Specification.class), any(Pageable.class));
    }
}
