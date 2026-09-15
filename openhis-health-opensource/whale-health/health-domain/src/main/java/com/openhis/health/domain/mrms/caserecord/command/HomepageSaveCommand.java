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
import java.util.List;

/**
 * 首页保存命令（领域写路径入参，Wave 1 + P0/V20 升列显式字段）。
 *
 * <p>非 null 标量写入 {@code mrms_case_record_homepage} 并标记 manual 来源；子表非 null 时全量替换。
 * 费用快照不经本命令写入；由「重新统计」或首次空快照补种落库，应用层叠到 DTO.fees。</p>
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

    /** 实施临床路径（1中医 2西医 3否）。 */
    private String clinicalPathCode;

    /** 使用医疗机构中药制剂（1是 2否）。 */
    private String tcmPrepCode;

    /** 使用中医诊疗设备（1是 2否）。 */
    private String tcmDeviceCode;

    /** 使用中医诊疗技术（1是 2否）。 */
    private String tcmTechCode;

    /** 辨证施护（1是 2否）。 */
    private String syndromeNurseCode;

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

    /** 医疗付费方式（原 extension.payWayCode）。 */
    private String payWayCode;

    /** 健康卡号。 */
    private String healthCardNo;

    /** 年龄岁。 */
    private Integer ageYear;

    /** 不足 1 周岁月龄。 */
    private Integer ageMonth;

    /** 出生地省市区 codes（API List；落库斜杠拼接，如 220000/220100）。 */
    private List<String> birplcCodes;

    /** 出生地中文地名（连续文本；卫统 CSD / HQMS A22）。 */
    private String birplcName;

    /** 籍贯省市 codes（API List；落库斜杠拼接）。 */
    private List<String> naplCodes;

    /** 籍贯中文地名（连续文本；卫统 GG。HQMS A23C 用 codes→RC036）。 */
    private String naplName;

    /** 现住址省市区 codes（API List；落库斜杠拼接）。 */
    private List<String> currAddrCodes;

    /** 现住址详细。 */
    private String currAddrDetail;

    /** 现住址邮编。 */
    private String currAddrPoscode;

    /** 户口地址省市区 codes（API List；落库斜杠拼接）。 */
    private List<String> resdAddrCodes;

    /** 户口详细地址。 */
    private String resdAddrDetail;

    /** 户口邮编。 */
    private String resdAddrPoscode;

    /** 工作单位及地址。 */
    private String emprAddr;

    /** 单位电话。 */
    private String emprTel;

    /** 单位邮编。 */
    private String emprPoscode;

    /** 入院病区。 */
    private String admWard;

    /** 入院床位。 */
    private String admBed;

    /** 出院病区。 */
    private String dscgWard;

    /** 出院床位。 */
    private String dscgBed;

    /** 药物过敏。 */
    private String drugAllergyCode;

    /** 过敏药物。 */
    private String allergyDrugName;

    /** 病理号。 */
    private String pathologyNo;

    /** 主要诊断治愈好转。 */
    private String mainDiagCureCode;

    /** 抢救次数。 */
    private Integer rescueCount;

    /** 抢救成功次数。 */
    private Integer rescueSuccessCount;

    /** 临床路径管理（异于中医 clinicalPathCode）。 */
    private String clinicPathManageCode;

    /** 诊断符合门诊与出院。 */
    private String diagMatchOpDscg;

    /** 诊断符合入院与出院。 */
    private String diagMatchAdmDscg;

    /** 诊断符合术前与术后。 */
    private String diagMatchPrePost;

    /** 诊断符合临床与病理。 */
    private String diagMatchClinicPath;

    /** 诊断符合放射与病理。 */
    private String diagMatchRadioPath;

    /** 死亡患者尸检。 */
    private String autopsyCode;

    /** 血型 ABO。 */
    private String aboCode;

    /** Rh。 */
    private String rhCode;

    /** 进修医师。 */
    private String trainDrName;

    /** 实习医师。 */
    private String intnDrName;

    /** 编码员。 */
    private String codrName;

    /** 病案质量。 */
    private String medcasQltCode;

    /** 质控日期。 */
    private LocalDate qltctrlDate;

    /** 质控医师。 */
    private String qltctrlDrName;

    /** 质控护士。 */
    private String qltctrlNursName;

    /** 拟接收医院。 */
    private String acpMedinsName;

    /** 拟接收社区。 */
    private String acpCommunityName;

    /** 31 天再住院计划。 */
    private String rinp31Flag;

    /** 再住院目的。 */
    private String rinp31Purpose;

    /** 诊断子表全量列表；null 表示本次保存不替换子表。 */
    private List<HomepageDiagnosis> diagnoses;

    /** 手术子表全量列表；null 表示本次保存不替换子表。 */
    private List<HomepageProcedure> procedures;
}
