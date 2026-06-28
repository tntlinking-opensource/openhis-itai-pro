/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.homepage.request;

import com.openhis.health.application.mrms.homepage.dto.DiagnosisLineDto;
import com.openhis.health.application.mrms.homepage.dto.ProcedureLineDto;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 保存病案首页请求。
 */
@Getter
@Setter
public class SaveHomepageRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 姓名。 */
    private String psnName;

    /** 性别字典码。 */
    private String gendCode;

    /** 出生日期。 */
    private LocalDate brdy;

    /** 证件号。 */
    private String certno;

    /** 住院号。 */
    private String iptNo;

    /** 入院途径字典码。 */
    private String admWayCode;

    /** 入院科别编码。 */
    private String admCatyCode;

    /** 入院时间。 */
    private LocalDateTime admTime;

    /** 出院科别编码。 */
    private String dscgCatyCode;

    /** 出院时间。 */
    private LocalDateTime dscgTime;

    /** 住院天数。 */
    private Integer iptDays;

    /** 离院方式字典码。 */
    private String dscgWayCode;

    /** 主任/副主任医师姓名。 */
    private String chfdrName;

    /** 主治医师姓名。 */
    private String atddrName;

    /** 住院医师姓名。 */
    private String iptDrName;

    /** 扩展字段。 */
    private Map<String, Object> extension = new HashMap<>();

    /** 诊断行全量列表。 */
    private List<DiagnosisLineDto> diagnoses = new ArrayList<>();

    /** 手术行全量列表。 */
    private List<ProcedureLineDto> procedures = new ArrayList<>();
}
