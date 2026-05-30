package com.openhis.whale.mrms.application.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ProcedureLineDto {
    private Long id;
    private Integer sortNo;
    private String oprnOprtCode;
    private String oprnOprtName;
    private LocalDate oprnDate;
    private String oprnLevelCode;
    private String oprnIncisionCode;
    private String healGradeCode;
    private String anstWayCode;
}
