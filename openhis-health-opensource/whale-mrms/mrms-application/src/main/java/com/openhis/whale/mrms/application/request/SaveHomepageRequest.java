package com.openhis.whale.mrms.application.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class SaveHomepageRequest {
    private Long caseRecordId;
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
    private Map<String, Object> extension;
    private List<DiagnosisLineRequest> diagnoses;
    private List<ProcedureLineRequest> procedures;

    @Getter
    @Setter
    public static class DiagnosisLineRequest {
        private String diagTypeCode;
        private Integer sortNo;
        private String diagCode;
        private String diagName;
        private String admCondCode;
    }

    @Getter
    @Setter
    public static class ProcedureLineRequest {
        private Integer sortNo;
        private String oprnOprtCode;
        private String oprnOprtName;
        private LocalDate oprnDate;
        private String oprnLevelCode;
        private String oprnIncisionCode;
        private String healGradeCode;
        private String anstWayCode;
    }
}
