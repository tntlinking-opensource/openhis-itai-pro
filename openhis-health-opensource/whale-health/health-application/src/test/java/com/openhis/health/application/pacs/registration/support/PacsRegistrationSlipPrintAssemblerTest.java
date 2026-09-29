/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.registration.support;

import com.openhis.health.application.pacs.registration.dto.PacsRegistrationSlipPrintDto;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.pacs.workitem.queries.model.PacsWorkItemDetailQueryModel;
import com.openhis.health.domain.pacs.workitem.service.PacsWorkItemQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PacsPrintScenarioConstants;
import com.openhis.health.domain.share.enums.PacsWorkItemStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * {@link PacsRegistrationSlipPrintAssembler} 序号 60：登记条打印 REG-05。
 */
@ExtendWith(MockitoExtension.class)
class PacsRegistrationSlipPrintAssemblerTest {

    @Mock
    private PacsWorkItemQueryService pacsWorkItemQueryService;

    @Mock
    private PatientQueryService patientQueryService;

    @Mock
    private OrganizationQueryService organizationQueryService;

    private PacsRegistrationSlipPrintAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new PacsRegistrationSlipPrintAssembler(
            pacsWorkItemQueryService, patientQueryService, organizationQueryService);
    }

    @Test
    void assemble_scheduledWorkItem_fillsSlipFields() {
        PacsWorkItemDetailQueryModel detail = new PacsWorkItemDetailQueryModel();
        detail.setId(1000L);
        detail.setPatientId(22L);
        detail.setEncounterId(200L);
        detail.setOrganizationId(300L);
        detail.setAccessionNumber("ACC1000");
        detail.setModalityCode("CT");
        detail.setBodySiteCode("HEAD");
        detail.setStatusEnum(PacsWorkItemStatus.SCHEDULED.getCode());
        when(pacsWorkItemQueryService.getDetail(1000L)).thenReturn(detail);

        Patient patient = new Patient();
        patient.setId(22L);
        patient.setName("张三");
        patient.setGenderEnum("male");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        when(patientQueryService.findOptionalPatientEntityById(22L)).thenReturn(Optional.of(patient));
        when(organizationQueryService.mapNamesByIds(anyCollection())).thenReturn(Map.of(300L, "影像科"));

        PacsRegistrationSlipPrintDto slip = assembler.assemble(1000L);

        assertEquals(PacsPrintScenarioConstants.REGISTRATION_SLIP_PRINT, slip.getScenarioCode());
        assertEquals(1000L, slip.getWorkItemId());
        assertEquals("ACC1000", slip.getAccessionNumber());
        assertEquals("张三", slip.getPatientName());
        assertEquals("男", slip.getGenderEnumText());
        assertEquals("影像科", slip.getOrganizationName());
        assertEquals("CT", slip.getModalityCode());
    }

    @Test
    void assemble_withoutAccession_throwsRuleConflict() {
        PacsWorkItemDetailQueryModel detail = new PacsWorkItemDetailQueryModel();
        detail.setId(1000L);
        detail.setStatusEnum(PacsWorkItemStatus.REGISTERED.getCode());
        when(pacsWorkItemQueryService.getDetail(1000L)).thenReturn(detail);

        BusinessException ex = assertThrows(BusinessException.class, () -> assembler.assemble(1000L));
        assertEquals(ErrorCodeConstants.PACS_WORK_ITEM_RULE_CONFLICT, ex.getErrorCode());
    }
}
