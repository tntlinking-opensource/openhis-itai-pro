/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.homepage.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 病案首页 DTO（标量 + 子表 + 扩展）。
 */
@Getter
@Setter
public class HomepageDto {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 首页版本号。 */
    private Integer versionNo;

    /** 是否冻结。 */
    private Boolean frozen;

    /** 4401 首页流水号。 */
    private String iptMedcasHmpgSn;

    /** 姓名。 */
    private String psnName;

    /** 性别字典码。 */
    private String gendCode;

    /** 出生日期。 */
    private LocalDate brdy;

    /** 国籍字典码。 */
    private String ntlyCode;

    /** 民族字典码。 */
    private String natyCode;

    /** 证件号。 */
    private String certno;

    /** 职业字典码。 */
    private String prfsCode;

    /** 婚姻状况字典码。 */
    private String mrgStasCode;

    /** 现住址。 */
    private String currAddr;

    /** 联系电话。 */
    private String psnTel;

    /** 联系人姓名。 */
    private String conerName;

    /** 联系人电话。 */
    private String conerTel;

    /** 住院号。 */
    private String iptNo;

    /** 入院途径字典码。 */
    private String admWayCode;

    /** 入院科别编码。 */
    private String admCatyCode;

    /** 入院科别名称。 */
    private String admCatyName;

    /** 入院时间。 */
    private LocalDateTime admTime;

    /** 出院科别编码。 */
    private String dscgCatyCode;

    /** 出院科别名称。 */
    private String dscgCatyName;

    /** 出院时间。 */
    private LocalDateTime dscgTime;

    /** 住院天数。 */
    private Integer iptDays;

    /** 转科路径摘要。 */
    private String transferDeptSummary;

    /** 离院方式字典码。 */
    private String dscgWayCode;

    /** 科主任姓名。 */
    private String deptDirectorName;

    /** 主任/副主任医师姓名。 */
    private String chfdrName;

    /** 主治医师姓名。 */
    private String atddrName;

    /** 住院医师姓名。 */
    private String iptDrName;

    /** 责任护士姓名。 */
    private String respNursName;

    /** 字段来源映射。 */
    private Map<String, String> fieldSources = new HashMap<>();

    /** 扩展字段。 */
    private Map<String, Object> extension = new HashMap<>();

    /** 诊断行列表。 */
    private List<DiagnosisLineDto> diagnoses = new ArrayList<>();

    /** 手术行列表。 */
    private List<ProcedureLineDto> procedures = new ArrayList<>();
}
