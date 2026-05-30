package com.openhis.whale.mrms.domain.port.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ProcedureLineSnapshot {
    private int sortNo;
    private String procedureCode;
    private String procedureName;
    private LocalDate procedureDate;
    private String levelCode;
    private String incisionCode;
    private String healGradeCode;
    private String anesthesiaCode;
}
