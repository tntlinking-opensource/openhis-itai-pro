/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.medcas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 【4101A】setlinfo 结算清单主信息（仅上送有值字段）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MedcasSettleListSetlinfo {

    /** 人员编号。 */
    @JsonProperty("psn_no")
    private String psnNo;

    /** 就诊 ID。 */
    @JsonProperty("mdtrt_id")
    private String mdtrtId;

    /** 结算 ID。 */
    @JsonProperty("setl_id")
    private String setlId;

    /** 病案号。 */
    @JsonProperty("medcasno")
    private String medcasno;

    /** 国籍。 */
    @JsonProperty("ntly")
    private String ntly;

    /** 职业。 */
    @JsonProperty("prfs")
    private String prfs;

    /** 现住址。 */
    @JsonProperty("curr_addr")
    private String currAddr;

    /** 联系人姓名。 */
    @JsonProperty("coner_name")
    private String conerName;

    /** 与患者关系。 */
    @JsonProperty("patn_rlts")
    private String patnRlts;

    /** 联系人地址。 */
    @JsonProperty("coner_addr")
    private String conerAddr;

    /** 联系人电话。 */
    @JsonProperty("coner_tel")
    private String conerTel;

    /** 入院途径。 */
    @JsonProperty("adm_way")
    private String admWay;

    /** 治疗类别。 */
    @JsonProperty("trt_type")
    private String trtType;

    /** 入院时间。 */
    @JsonProperty("adm_time")
    private String admTime;

    /** 转科科别。 */
    @JsonProperty("refldept_dept")
    private String refldeptDept;

    /** 出院时间。 */
    @JsonProperty("dscg_time")
    private String dscgTime;

    /** 出院科别。 */
    @JsonProperty("dscg_caty")
    private String dscgCaty;

    /** 门急诊西医诊断。 */
    @JsonProperty("otp_wm_dise")
    private String otpWmDise;

    /** 西医疾病代码。 */
    @JsonProperty("wm_dise_code")
    private String wmDiseCode;

    /** 门急诊中医诊断。 */
    @JsonProperty("otp_tcm_dise")
    private String otpTcmDise;

    /** 中医疾病代码。 */
    @JsonProperty("tcm_dise_code")
    private String tcmDiseCode;

    /** 特级护理天数。 */
    @JsonProperty("spga_nurscare_days")
    private String spgaNurscareDays;

    /** 一级护理天数。 */
    @JsonProperty("lv1_nurscare_days")
    private String lv1NurscareDays;

    /** 二级护理天数。 */
    @JsonProperty("scd_nurscare_days")
    private String scdNurscareDays;

    /** 三级护理天数。 */
    @JsonProperty("lv3_nurscare_days")
    private String lv3NurscareDays;

    /** 离院方式。 */
    @JsonProperty("dscg_way")
    private String dscgWay;

    /** 票据代码。 */
    @JsonProperty("bill_code")
    private String billCode;

    /** 票据号码。 */
    @JsonProperty("bill_no")
    private String billNo;

    /** 业务流水号。 */
    @JsonProperty("biz_sn")
    private String bizSn;

    /** 出院 31 天内再住院计划标志。 */
    @JsonProperty("days_rinp_flag_31")
    private String daysRinpFlag31;

    /** 出院 31 天内再住院计划目的。 */
    @JsonProperty("days_rinp_pup_31")
    private String daysRinpPup31;

    /** 结算开始日期。 */
    @JsonProperty("setl_begn_date")
    private String setlBegnDate;

    /** 结算结束日期。 */
    @JsonProperty("setl_end_date")
    private String setlEndDate;

    /** 医疗机构填报部门。 */
    @JsonProperty("medins_fill_dept")
    private String medinsFillDept;

    /** 医疗机构填报人。 */
    @JsonProperty("medins_fill_psn")
    private String medinsFillPsn;

    /** 主诊医师代码（医保医师码 / 就医地唯一编号）。 */
    @JsonProperty("chfpdr_code")
    private String chfpdrCode;

    /** 责任护士代码（医保护理人员码；首页仅姓名时不上送）。 */
    @JsonProperty("resp_nurs_code")
    private String respNursCode;

    /**
     * 医保支付方式（字典 hi_paymtd：1 按项目 / 2 单病种 / 5 按床日 / 7 按定额 / 9 其他）。
     *
     * <p>由个人结算方式 {@code psn_setlway} 映射；ZXPlat 结算固定 01→本字段 1。</p>
     */
    @JsonProperty("hi_paymtd")
    private String hiPaymtd;

    /** 入院科室编码（国家版本新增；科室医保 yb_no）。 */
    @JsonProperty("adm_dept_codg")
    private String admDeptCodg;

    /** 入院科室名称（国家版本新增）。 */
    @JsonProperty("adm_dept_name")
    private String admDeptName;
}
