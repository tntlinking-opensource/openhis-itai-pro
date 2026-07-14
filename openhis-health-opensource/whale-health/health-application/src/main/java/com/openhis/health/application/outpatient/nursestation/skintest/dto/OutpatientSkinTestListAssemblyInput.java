/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.nursestation.skintest.dto;

import com.openhis.health.domain.clinical.allergyintolerance.queries.model.AllergyIntoleranceRecordQueryModel;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * IF-OP-STK-02 列表装配入参（读侧批量结果一次性传入 Assembler）。
 */
@Getter
@Setter
public class OutpatientSkinTestListAssemblyInput {

    private Long encounterId;
    private Long patientId;
    private List<ServiceRequestListModel> skinTestServiceRequests;
    private Map<Long, MedicationRequest> medicationRequestById;
    private Map<Long, String> medicationNameById;
    private Map<Long, Long> locationIdByMedicationRequestId;
    private Map<Long, AllergyIntoleranceRecordQueryModel> allergyByServiceRequestId;
    private Map<String, List<OutpatientSkinTestLotOptionDto>> lotOptionsByMedLocationKey;
    private Map<Long, String> practitionerNameById;
}
