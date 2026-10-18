/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.appointment.service;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import com.zxplat.plugin.booking.domain.appointment.command.CreateAppointmentCommand;
import com.zxplat.plugin.booking.domain.appointment.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AppointmentDomainService 关键：创建批次须恰好一条。
 */
@ExtendWith(MockitoExtension.class)
class AppointmentDomainServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private IDomainEventPublisher domainEventPublisher;
    @Mock
    private BusinessSequenceService businessSequenceService;

    @InjectMocks
    private AppointmentDomainService appointmentDomainService;

    @Test
    void create_rejectsEmptyBatch() {
        CreateAppointmentCommand command = new CreateAppointmentCommand();
        command.setItems(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appointmentDomainService.create(command));
        assertEquals(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, ex.getErrorCode());
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void create_rejectsMultipleItems() {
        CreateAppointmentCommand.CreateAppointmentItemCommand a =
                new CreateAppointmentCommand.CreateAppointmentItemCommand();
        CreateAppointmentCommand.CreateAppointmentItemCommand b =
                new CreateAppointmentCommand.CreateAppointmentItemCommand();
        CreateAppointmentCommand command = new CreateAppointmentCommand();
        command.setItems(List.of(a, b));

        assertThrows(BusinessException.class, () -> appointmentDomainService.create(command));
        verify(appointmentRepository, never()).save(any());
    }
}
