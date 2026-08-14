/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.command;

import com.openhis.health.domain.mrms.caserecord.entity.HomepageDiagnosis;
import com.openhis.health.domain.mrms.caserecord.entity.HomepageProcedure;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页保存命令（领域写路径入参，Wave 1 + P0 扩列显式字段）。
 *
 * <p>非 null 标量写入 {@code mrms_case_record_homepage} 并标记 manual 来源；子表非 null 时全量替换。</p>
 */
@Getter
@Setter
public class HomepageSaveCommand {

    /** 病案主档主键 {@code mrms_case_record.id}。 */
    private Long caseRecordId;

    /** 操作人 {@code adm_practitioner.id}，写入审计字段。 */
    private Long operatorId;

    /** 患者姓名（4401 psn_name）。 */
    private String psnName;

    /** 性别字典码（4401 gend）。 */
    private String gendCode;

    /** 出生日期（4401 brdy）。 */
    private LocalDate brdy;

    /** 国籍字典码（4401 ntly）。 */
    private String ntlyCode;

    /** 民族字典码（4401 naty）。 */
    private String natyCode;

    /** 证件号码（4401 certno）。 */
    private String certno;

    /** 职业字典码（4401 prfs）。 */
    private String prfsCode;

    /** 婚姻状况字典码（4401 mrg_stas）。 */
    private String mrgStasCode;

    /** 现住址（4401 curr_addr）。 */
    private String currAddr;

    /** 联系电话（4401 psn_tel）。 */
    private String psnTel;

    /** 联系人姓名（4401 coner_name）。 */
    private String conerName;

    /** 联系人电话（4401 coner_tel）。 */
    private String conerTel;

    /** 联系人关系字典码（4401 coner_rlts）。 */
    private String conerRltsCode;

    /** 联系人地址（4401 coner_addr）。 */
    private String conerAddr;

    /** 治疗类别字典码（4401 trt_type）。 */
    private String trtTypeCode;

    /** 特级护理天数（4401 spga_nurscare_days）。 */
    private Integer spgaNurscareDays;

    /** 一级护理天数（4401 lv1_nurscare_days）。 */
    private Integer lv1NurscareDays;

    /** 二级护理天数（4401 scd_nurscare_days）。 */
    private Integer scdNurscareDays;

    /** 三级护理天数（4401 lv3_nurscare_days）。 */
    private Integer lv3NurscareDays;

    /** 门急诊西医诊断名称（4401 otp_wm_dise）。 */
    private String otpWmDise;

    /** 门急诊西医诊断编码（4401 wm_dise_code）。 */
    private String wmDiseCode;

    /** 门急诊中医诊断名称（4401 otp_tcm_dise）。 */
    private String otpTcmDise;

    /** 门急诊中医诊断编码（4401 tcm_dise_code）。 */
    private String tcmDiseCode;

    /** 入院前颅脑损伤昏迷时长（天/时/分文本）。 */
    private String brnDamgBfadmComaDura;

    /** 入院后颅脑损伤昏迷时长（天/时/分文本）。 */
    private String brnDamgAfadmComaDura;

    /** 呼吸机使用时长（天/时/分文本）。 */
    private String ventUsedDura;

    /** 住院号（4401 关联住院号 / {@code adm_encounter.bus_no}）。 */
    private String iptNo;

    /** 入院途径字典码（4401 adm_way，提交校验必填）。 */
    private String admWayCode;

    /** 入院科别编码（4401 adm_caty）。 */
    private String admCatyCode;

    /** 入院科别名称（展示名，列表冗余同步用）。 */
    private String admCatyName;

    /** 入院时间（4401 adm_time）。 */
    private LocalDateTime admTime;

    /** 出院科别编码（4401 dscg_caty）。 */
    private String dscgCatyCode;

    /** 出院科别名称（展示名，列表冗余同步用）。 */
    private String dscgCatyName;

    /** 出院时间（4401 dscg_time，须不早于 adm_time）。 */
    private LocalDateTime dscgTime;

    /** 住院天数（4401 ipt_days）。 */
    private Integer iptDays;

    /** 转科路径摘要（科室 A→B→C）。 */
    private String transferDeptSummary;

    /** 离院方式字典码（4401 dscg_way，提交校验必填）。 */
    private String dscgWayCode;

    /** 科主任姓名（4401 dept_director）。 */
    private String deptDirectorName;

    /** 主任/副主任医师姓名（4401 chfdr，提交校验必填）。 */
    private String chfdrName;

    /** 主治医师姓名（4401 atddr，提交校验必填）。 */
    private String atddrName;

    /** 住院医师姓名（4401 ipt_dr，提交校验必填）。 */
    private String iptDrName;

    /** 责任护士姓名（4401 resp_nurs）。 */
    private String respNursName;

    /** 扩展 JSON 键值（合并入 {@code extension_json}，如费用分项）。 */
    private Map<String, Object> extension = new HashMap<>();

    /** 诊断子表全量列表；null 表示本次保存不替换子表。 */
    private List<HomepageDiagnosis> diagnoses;

    /** 手术子表全量列表；null 表示本次保存不替换子表。 */
    private List<HomepageProcedure> procedures;
}
