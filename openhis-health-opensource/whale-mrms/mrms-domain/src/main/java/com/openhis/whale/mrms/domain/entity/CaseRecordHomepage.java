package com.openhis.whale.mrms.domain.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 病案首页。表 mrms_case_record_homepage。
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_case_record_homepage")
public class CaseRecordHomepage extends BaseAuditedEntity {

    @Column(name = "case_record_id", nullable = false, unique = true)
    private Long caseRecordId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    @Column(name = "frozen", nullable = false)
    private Boolean frozen = false;

    @Column(name = "psn_name", length = 100)
    private String psnName;

    @Column(name = "gend_code", length = 16)
    private String gendCode;

    @Column(name = "brdy")
    private LocalDate brdy;

    @Column(name = "certno", length = 50)
    private String certno;

    @Column(name = "ntly_code", length = 16)
    private String ntlyCode;

    @Column(name = "naty_code", length = 16)
    private String natyCode;

    @Column(name = "prfs_code", length = 16)
    private String prfsCode;

    @Column(name = "mrg_stas_code", length = 16)
    private String mrgStasCode;

    @Column(name = "curr_addr", length = 500)
    private String currAddr;

    @Column(name = "psn_tel", length = 50)
    private String psnTel;

    @Column(name = "coner_name", length = 100)
    private String conerName;

    @Column(name = "coner_tel", length = 50)
    private String conerTel;

    @Column(name = "coner_addr", length = 500)
    private String conerAddr;

    @Column(name = "ipt_no", length = 32)
    private String iptNo;

    @Column(name = "adm_way_code", length = 16)
    private String admWayCode;

    @Column(name = "adm_caty_code", length = 64)
    private String admCatyCode;

    @Column(name = "adm_caty_name", length = 200)
    private String admCatyName;

    @Column(name = "dscg_caty_code", length = 64)
    private String dscgCatyCode;

    @Column(name = "dscg_caty_name", length = 200)
    private String dscgCatyName;

    @Column(name = "adm_time")
    private LocalDateTime admTime;

    @Column(name = "dscg_time")
    private LocalDateTime dscgTime;

    @Column(name = "act_ipt_days")
    private Integer actIptDays;

    @Column(name = "dscg_way_code", length = 16)
    private String dscgWayCode;

    @Column(name = "transfer_dept_summary", length = 1000)
    private String transferDeptSummary;

    @Column(name = "dept_director_name", length = 100)
    private String deptDirectorName;

    @Column(name = "chfdr_name", length = 100)
    private String chfdrName;

    @Column(name = "atddr_name", length = 100)
    private String atddrName;

    @Column(name = "ipt_dr_name", length = 100)
    private String iptDrName;

    @Column(name = "resp_nurs_name", length = 100)
    private String respNursName;

    @Column(name = "field_sources_json", columnDefinition = "TEXT")
    private String fieldSourcesJson;

    @Column(name = "extension_json", columnDefinition = "TEXT")
    private String extensionJson;
}
