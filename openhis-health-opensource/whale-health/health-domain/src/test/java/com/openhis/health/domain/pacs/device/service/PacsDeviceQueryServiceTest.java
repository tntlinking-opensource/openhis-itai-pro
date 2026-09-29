/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.device.service;

import com.openhis.health.domain.pacs.device.entity.PacsDevice;
import com.openhis.health.domain.pacs.device.repository.PacsDeviceRepository;
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
 * {@link PacsDeviceQueryService} 读侧单测。
 */
@ExtendWith(MockitoExtension.class)
class PacsDeviceQueryServiceTest {

    @Mock
    private PacsDeviceRepository deviceRepository;

    @InjectMocks
    private PacsDeviceQueryService queryService;

    @Test
    @SuppressWarnings("unchecked")
    void search_invokesFindAll() {
        Page<PacsDevice> empty =
            new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(deviceRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(empty);

        queryService.search(null, null, null, PageRequest.of(0, 10));

        verify(deviceRepository).findAll(any(Specification.class), any(Pageable.class));
    }
}
