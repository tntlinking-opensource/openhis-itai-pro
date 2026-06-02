package com.openhis.whale.mrms.application.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DiagnosisLineDto {
    private Long id;
    private String diagTypeCode;
    private Integer sortNo;
    private String diagCode;
    private String diagName;
    private String admCondCode;
}
