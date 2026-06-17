package com.openhis.whale.mrms.port.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class PatientSnapshot {
    private Long patientId;
    private String name;
    private String genderCode;
    private LocalDate birthDate;
    private String idCard;
    private String nationalityCode;
    private String nationalityText;
    private String maritalStatusCode;
    private String occupationCode;
    private String phone;
    private String address;
    private String contactName;
    private String contactPhone;
    private String contactAddress;
}
