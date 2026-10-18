/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.caserecord.homepage.request;

import com.zxplat.application.mrms.caserecord.homepage.dto.BldLineDto;
import com.zxplat.application.mrms.caserecord.homepage.dto.DiagnosisLineDto;
import com.zxplat.application.mrms.caserecord.homepage.dto.IcuLineDto;
import com.zxplat.application.mrms.caserecord.homepage.dto.ProcedureLineDto;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    /** 实施临床路径（国标：1中医 2西医 3否）。 */
    private String clinicalPathCode;

    /** 使用医疗机构中药制剂（1是 2否）。 */
    private String tcmPrepCode;

    /** 使用中医诊疗设备（1是 2否）。 */
    private String tcmDeviceCode;

    /** 使用中医诊疗技术（1是 2否）。 */
    private String tcmTechCode;

    /** 辨证施护（1是 2否）。 */
    private String syndromeNurseCode;

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

    /** 医疗付费方式。 */
    private String payWayCode;

    /** 健康卡号。 */
    private String healthCardNo;

    /** 年龄岁。 */
    private Integer ageYear;

    /** 不足 1 周岁月龄。 */
    private Integer ageMonth;

    /** 出生地省市区 codes（API 为 List；库列斜杠拼接）。 */
    private List<String> birplcCodes;

    /** 出生地中文地名（连续文本；卫统 CSD / HQMS A22）。 */
    private String birplcName;

    /** 籍贯省市 codes（API 为 List；库列斜杠拼接）。 */
    private List<String> naplCodes;

    /** 籍贯中文地名（连续文本；卫统 GG。HQMS A23C 用 codes→RC036）。 */
    private String naplName;

    /** 现住址省市区 codes（API 为 List；库列斜杠拼接）。 */
    private List<String> currAddrCodes;

    /** 现住址详细。 */
    private String currAddrDetail;

    /** 现住址邮编。 */
    private String currAddrPoscode;

    /** 户口地址省市区 codes（API 为 List；库列斜杠拼接）。 */
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

    /** 诊断行全量列表。 */
    private List<DiagnosisLineDto> diagnoses = new ArrayList<>();

    /** 手术行全量列表。 */
    private List<ProcedureLineDto> procedures = new ArrayList<>();

    /** 重症监护行全量列表。 */
    private List<IcuLineDto> icus = new ArrayList<>();

    /** 输血行全量列表。 */
    private List<BldLineDto> blds = new ArrayList<>();
}
