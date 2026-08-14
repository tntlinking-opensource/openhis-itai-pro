/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.caserecord.homepage.request;

import com.openhis.health.application.mrms.caserecord.homepage.dto.DiagnosisLineDto;
import com.openhis.health.application.mrms.caserecord.homepage.dto.ProcedureLineDto;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 保存病案首页请求（Wave 1 + P0 扩列）。
 *
 * <p>无 {@code caseRecordId} 时须传 {@code encounterId}，应用层先幂等建档再保存。</p>
 */
@Getter
@Setter
public class SaveHomepageRequest {

    /**
     * 病案主档主键；空表示尚未建档，须配合 {@link #encounterId} 先 bootstrap。
     */
    private Long caseRecordId;

    /**
     * 住院就诊主键；{@link #caseRecordId} 为空时必填，用于首次保存幂等建档。
     */
    private Long encounterId;

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

    /** 联系人关系字典码。 */
    private String conerRltsCode;

    /** 联系人地址。 */
    private String conerAddr;

    /** 治疗类别字典码。 */
    private String trtTypeCode;

    /** 特级护理天数。 */
    private Integer spgaNurscareDays;

    /** 一级护理天数。 */
    private Integer lv1NurscareDays;

    /** 二级护理天数。 */
    private Integer scdNurscareDays;

    /** 三级护理天数。 */
    private Integer lv3NurscareDays;

    /** 门急诊西医诊断名称。 */
    private String otpWmDise;

    /** 门急诊西医诊断编码。 */
    private String wmDiseCode;

    /** 门急诊中医诊断名称。 */
    private String otpTcmDise;

    /** 门急诊中医诊断编码。 */
    private String tcmDiseCode;

    /** 入院前颅脑损伤昏迷时长（天/时/分文本）。 */
    private String brnDamgBfadmComaDura;

    /** 入院后颅脑损伤昏迷时长（天/时/分文本）。 */
    private String brnDamgAfadmComaDura;

    /** 呼吸机使用时长（天/时/分文本）。 */
    private String ventUsedDura;

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

    /** 扩展字段。 */
    private Map<String, Object> extension = new HashMap<>();

    /** 诊断行全量列表。 */
    private List<DiagnosisLineDto> diagnoses = new ArrayList<>();

    /** 手术行全量列表。 */
    private List<ProcedureLineDto> procedures = new ArrayList<>();
}
