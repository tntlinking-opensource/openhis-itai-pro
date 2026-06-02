package com.openhis.whale.mrms.domain.port.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DiagnosisLineSnapshot {
    private int sortNo;
    private String diagCode;
    private String diagName;
    private String admCondCode;
    private boolean primary;
}
