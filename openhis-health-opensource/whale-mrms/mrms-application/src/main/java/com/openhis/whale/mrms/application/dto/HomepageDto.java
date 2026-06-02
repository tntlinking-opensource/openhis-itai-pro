package com.openhis.whale.mrms.application.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class HomepageDto {
    private Long caseRecordId;
    private Integer versionNo;
    private Boolean frozen;
    private String psnName;
    private String gendCode;
    private LocalDate brdy;
    private String certno;
    private String ntlyCode;
    private String natyCode;
    private String prfsCode;
    private String mrgStasCode;
    private String currAddr;
    private String psnTel;
    private String conerName;
    private String conerTel;
    private String conerAddr;
    private String iptNo;
    private String admWayCode;
    private String admCatyCode;
    private String admCatyName;
    private String dscgCatyCode;
    private String dscgCatyName;
    private LocalDateTime admTime;
    private LocalDateTime dscgTime;
    private Integer actIptDays;
    private String dscgWayCode;
    private String transferDeptSummary;
    private String deptDirectorName;
    private String chfdrName;
    private String atddrName;
    private String iptDrName;
    private String respNursName;
    private Map<String, String> fieldSources;
    private Map<String, Object> extension;
    private List<DiagnosisLineDto> diagnoses;
    private List<ProcedureLineDto> procedures;
}
