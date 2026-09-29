/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.registration.service;

import com.openhis.health.application.pacs.registration.mapper.PacsRegistrationMapper;
import com.openhis.health.application.pacs.registration.request.CreateLocalRegistrationRequest;
import com.openhis.health.application.pacs.registration.response.CreateLocalRegistrationResponse;
import com.openhis.health.application.pacs.registration.support.PacsLocalRegistrationEncounterResolver;
import com.openhis.health.application.pacs.registration.support.PacsRegistrationSlipPrintAssembler;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.pacs.workitem.command.CreateLocalPacsWorkItemCommand;
import com.openhis.health.domain.pacs.workitem.command.UpdatePacsRegistrationCommand;
import com.openhis.health.domain.pacs.workitem.entity.PacsWorkItem;
import com.openhis.health.domain.pacs.workitem.service.PacsWorkItemDomainService;
import com.openhis.health.domain.pacs.workitem.service.PacsWorkItemQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PacsWorkItemStatus;
import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PacsRegistrationAppService} 序号 59：本地补登记 REG-04。
 */
@ExtendWith(MockitoExtension.class)
class PacsRegistrationAppServiceLocalRegistrationTest {

    @Mock
    private PacsWorkItemQueryService pacsWorkItemQueryService;

    @Mock
    private PacsWorkItemDomainService pacsWorkItemDomainService;

    @Mock
    private PatientQueryService patientQueryService;

    @Mock
    private CurrentPractitionerResolver currentPractitionerResolver;

    @Mock
    private PageWindowGate pageWindowGate;

    @Mock
    private PacsLocalRegistrationEncounterResolver localRegistrationEncounterResolver;

    @Mock
    private PacsRegistrationSlipPrintAssembler registrationSlipPrintAssembler;

    @Mock
    private TenantContext tenantContext;

    private PacsRegistrationAppService appService;

    @BeforeEach
    void setUp() throws Exception {
        appService = new PacsRegistrationAppService(
            pacsWorkItemQueryService,
            pacsWorkItemDomainService,
            patientQueryService,
            currentPractitionerResolver,
            pageWindowGate,
            new PacsRegistrationMapper(),
            localRegistrationEncounterResolver,
            registrationSlipPrintAssembler);
        when(tenantContext.getCurrentTenantId()).thenReturn(1L);
        Field tenantContextField = TenantUtils.class.getDeclaredField("tenantContext");
        tenantContextField.setAccessible(true);
        tenantContextField.set(null, tenantContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        Field tenantContextField = TenantUtils.class.getDeclaredField("tenantContext");
        tenantContextField.setAccessible(true);
        tenantContextField.set(null, null);
    }

    @Test
    void createLocalRegistration_createsWorkItemAndSchedulesByDefault() {
        when(currentPractitionerResolver.requireOperatorId(
            eq(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT), any())).thenReturn(9L);
        Patient patient = new Patient();
        patient.setId(22L);
        when(patientQueryService.findOptionalPatientEntityById(22L)).thenReturn(Optional.of(patient));
        when(localRegistrationEncounterResolver.resolveEncounterId(
            eq(22L), eq(300L), eq(null), eq(501L), eq(9L))).thenReturn(200L);

        PacsWorkItem created = new PacsWorkItem();
        created.setId(1000L);
        created.initRegistered();
        when(pacsWorkItemDomainService.createLocalWorkItem(any(CreateLocalPacsWorkItemCommand.class)))
            .thenReturn(created);

        PacsWorkItem scheduled = new PacsWorkItem();
        scheduled.setId(1000L);
        scheduled.initRegistered();
        scheduled.markScheduledWithAccession("ACC1000");
        when(pacsWorkItemDomainService.updateRegistration(any(UpdatePacsRegistrationCommand.class)))
            .thenReturn(scheduled);

        CreateLocalRegistrationRequest request = new CreateLocalRegistrationRequest();
        request.setPatientId(22L);
        request.setOrganizationId(300L);
        request.setModalityCode("CT");
        request.setServiceTypeId(501L);

        CreateLocalRegistrationResponse response = appService.createLocalRegistration(request);

        assertNotNull(response);
        assertEquals(1000L, response.getWorkItemId());
        assertEquals(200L, response.getEncounterId());
        assertEquals("ACC1000", response.getAccessionNumber());
        assertEquals(PacsWorkItemStatus.SCHEDULED.getCode(), response.getStatusEnum());

        ArgumentCaptor<CreateLocalPacsWorkItemCommand> createCaptor =
            ArgumentCaptor.forClass(CreateLocalPacsWorkItemCommand.class);
        verify(pacsWorkItemDomainService).createLocalWorkItem(createCaptor.capture());
        assertEquals(22L, createCaptor.getValue().getPatientId());
        assertEquals(200L, createCaptor.getValue().getEncounterId());
        assertEquals(1L, createCaptor.getValue().getTenantId());

        ArgumentCaptor<UpdatePacsRegistrationCommand> scheduleCaptor =
            ArgumentCaptor.forClass(UpdatePacsRegistrationCommand.class);
        verify(pacsWorkItemDomainService).updateRegistration(scheduleCaptor.capture());
        assertEquals(Boolean.TRUE, scheduleCaptor.getValue().getConfirmSchedule());
    }
}
