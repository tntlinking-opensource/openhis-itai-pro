package com.openhis.whale.mrms.application.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CaseRecordDetailDto extends CaseRecordSummaryDto {
    private Integer patnIptCnt;
    private String deptCode;
    private Long attendingPractitionerId;
    private String attendingPractitionerName;
    private String returnReason;
    private String qualityGradeCode;
}
