/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.registration.service;

import com.zxplat.plugin.pacs.application.registration.mapper.PacsRegistrationMapper;
import com.zxplat.plugin.pacs.application.registration.request.CreateLocalRegistrationRequest;
import com.zxplat.plugin.pacs.application.registration.response.CreateLocalRegistrationResponse;
import com.zxplat.plugin.pacs.application.registration.support.PacsLocalRegistrationEncounterResolver;
import com.zxplat.plugin.pacs.application.registration.support.PacsRegistrationSlipPrintAssembler;
import com.zxplat.plugin.pacs.application.share.PacsCurrentPractitionerResolver;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.domain.administration.patient.entity.Patient;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.plugin.pacs.domain.workitem.command.CreateLocalPacsWorkItemCommand;
import com.zxplat.plugin.pacs.domain.workitem.command.UpdatePacsRegistrationCommand;
import com.zxplat.plugin.pacs.domain.workitem.entity.PacsWorkItem;
import com.zxplat.plugin.pacs.domain.workitem.service.PacsWorkItemDomainService;
import com.zxplat.plugin.pacs.domain.workitem.service.PacsWorkItemQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.PacsWorkItemStatus;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantContext;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
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
    private PacsCurrentPractitionerResolver currentPractitionerResolver;

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
