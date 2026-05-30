package com.openhis.whale.mrms.application.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CaseRecordSummaryDto {
    private Long id;
    private Long encounterId;
    private Long patientId;
    private String medcasno;
    private String statusCode;
    private String patientName;
    private String genderCode;
    private String inpatientNo;
    private String deptName;
    private LocalDateTime admissionAt;
    private LocalDateTime dischargeAt;
    private String primaryDiagnosisText;
}
