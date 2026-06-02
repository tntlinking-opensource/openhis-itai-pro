package com.openhis.whale.mrms.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 首页保存命令（领域层入参）。
 */
@Getter
@Setter
public class HomepageSaveCommand {

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
    private String fieldSourcesJson;
    private String extensionJson;
    private List<DiagnosisLineCommand> diagnoses;
    private List<ProcedureLineCommand> procedures;

    @Getter
    @Setter
    public static class DiagnosisLineCommand {
        private String diagTypeCode;
        private int sortNo;
        private String diagCode;
        private String diagName;
        private String admCondCode;
    }

    @Getter
    @Setter
    public static class ProcedureLineCommand {
        private int sortNo;
        private String oprnOprtCode;
        private String oprnOprtName;
        private LocalDate oprnDate;
        private String oprnLevelCode;
        private String oprnIncisionCode;
        private String healGradeCode;
        private String anstWayCode;
    }
}
