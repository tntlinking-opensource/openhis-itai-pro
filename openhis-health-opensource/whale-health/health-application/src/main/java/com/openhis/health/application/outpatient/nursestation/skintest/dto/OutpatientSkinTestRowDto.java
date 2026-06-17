/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.nursestation.skintest.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IF-OP-STK-02 皮试列表行。
 */
@Getter
@Setter
public class OutpatientSkinTestRowDto {

    private Long encounterId;
    private Long patientId;
    private Long serviceRequestId;
    private Long medicationRequestId;
    private Long medicationId;
    private String medicationName;
    private Long openDoctorId;
    private String openDoctorName;
    private Long performLocationId;
    private Long allergyRecordId;
    private String lotNumber;
    private List<OutpatientSkinTestLotOptionDto> lotNumberOptions;
    private LocalDateTime recordedDate;
    private String clinicalStatusCode;
    private String clinicalStatusText;
    private String verificationStatusCode;
    private String verificationStatusText;
    private Long practitionerId;
    private String practitionerName;
    private Long checkPractitionerId;
    private String checkPractitionerName;
    private String description;
    /** 列表已过滤 COMPLETED，恒 true。 */
    private Boolean feeSettledFlag;
}
