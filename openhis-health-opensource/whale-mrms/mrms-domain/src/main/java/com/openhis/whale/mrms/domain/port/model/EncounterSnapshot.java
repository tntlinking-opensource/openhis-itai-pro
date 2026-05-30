package com.openhis.whale.mrms.domain.port.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class EncounterSnapshot {
    private Long encounterId;
    private Long patientId;
    private String inpatientNo;
    private LocalDateTime admissionAt;
    private LocalDateTime dischargeAt;
    private String deptCode;
    private String deptName;
    private Long attendingPractitionerId;
    private String attendingPractitionerName;
    private String deptDirectorName;
    private String chiefDoctorName;
    private String residentDoctorName;
    private String primaryNurseName;
    private String admitWayCode;
    private String dischargeWayCode;
    private String admitDeptCode;
    private String admitDeptName;
    private String dischargeDeptCode;
    private String dischargeDeptName;
}
