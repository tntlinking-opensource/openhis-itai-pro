/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.medcas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 4401 病案首页 baseinfo 节点（首批实现字段）。
 *
 * <p>职责：与医保平台 JSON 字段一一对应，由 mapper 从 MRMS 病案首页组装。</p>
 */
@Data
public class MedcasHomepageBaseInfo {

    /** 就医流水号（院内就诊唯一标识）。 */
    @JsonProperty("mdtrt_sn")
    private String mdtrtSn;

    /** 医保就诊 ID（平台 mdtrt_id）。 */
    @JsonProperty("mdtrt_id")
    private String mdtrtId;

    /** 人员编号（医保 psn_no）。 */
    @JsonProperty("psn_no")
    private String psnNo;

    /** 患者住院次数。 */
    @JsonProperty("patn_ipt_cnt")
    private String patnIptCnt;

    /** 住院号。 */
    @JsonProperty("ipt_no")
    private String iptNo;

    /** 病案号。 */
    private String medcasno;

    /** 患者姓名。 */
    @JsonProperty("psn_name")
    private String psnName;

    /** 性别 code。 */
    private String gend;

    /** 出生日期。 */
    private String brdy;

    /** 国籍。 */
    private String ntly;

    /** 民族。 */
    private String naty;

    /** 证件号码。 */
    private String certno;

    /** 职业。 */
    private String prfs;

    /** 婚姻状况 code。 */
    @JsonProperty("mrg_stas")
    private String mrgStas;

    /** 现住址。 */
    @JsonProperty("curr_addr")
    private String currAddr;

    /** 本人电话。 */
    @JsonProperty("psn_tel")
    private String psnTel;

    /** 联系人姓名。 */
    @JsonProperty("coner_name")
    private String conerName;

    /** 联系人电话。 */
    @JsonProperty("coner_tel")
    private String conerTel;

    /** 入院途径 code。 */
    @JsonProperty("adm_way_code")
    private String admWayCode;

    /** 入院科别。 */
    @JsonProperty("adm_caty")
    private String admCaty;

    /** 出院科别。 */
    @JsonProperty("dscg_caty")
    private String dscgCaty;

    /** 入院日期。 */
    @JsonProperty("adm_date")
    private String admDate;

    /** 出院日期。 */
    @JsonProperty("dscg_date")
    private String dscgDate;

    /** 住院天数。 */
    @JsonProperty("ipt_days")
    private String iptDays;

    /** 离院方式 code。 */
    @JsonProperty("dscg_way")
    private String dscgWay;

    /** 科主任姓名。 */
    @JsonProperty("deptdrt_name")
    private String deptdrtName;

    /** 主任医师姓名。 */
    @JsonProperty("chfdr_name")
    private String chfdrName;

    /** 主治医师姓名。 */
    @JsonProperty("atddr_name")
    private String atddrName;

    /** 住院医师姓名。 */
    @JsonProperty("ipt_dr_name")
    private String iptDrName;

    /** 责任护士姓名。 */
    @JsonProperty("resp_nurs_name")
    private String respNursName;

    /** 住院总费用。 */
    @JsonProperty("medfee_sumamt")
    private String medfeeSumamt;

    /** 自付金额。 */
    @JsonProperty("selfpay_amt")
    private String selfpayAmt;

    /** 医疗费用支付方式 code。 */
    @JsonProperty("medfee_paymtd_code")
    private String medfeePaymtdCode;

    /** 定点医药机构编号。 */
    @JsonProperty("fixmedins_code")
    private String fixmedinsCode;

    /** 有效标志（1 有效 / 0 无效）。 */
    @JsonProperty("vali_flag")
    private String valiFlag;
}
