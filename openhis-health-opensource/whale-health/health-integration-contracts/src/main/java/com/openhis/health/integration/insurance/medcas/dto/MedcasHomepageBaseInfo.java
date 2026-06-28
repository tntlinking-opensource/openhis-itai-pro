/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.medcas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 4401 baseinfo 节点（首批实现字段）。
 */
@Data
public class MedcasHomepageBaseInfo {

    @JsonProperty("mdtrt_sn")
    private String mdtrtSn;

    @JsonProperty("mdtrt_id")
    private String mdtrtId;

    @JsonProperty("psn_no")
    private String psnNo;

    @JsonProperty("patn_ipt_cnt")
    private String patnIptCnt;

    @JsonProperty("ipt_no")
    private String iptNo;

    private String medcasno;

    @JsonProperty("psn_name")
    private String psnName;

    private String gend;

    private String brdy;

    private String ntly;

    private String naty;

    private String certno;

    private String prfs;

    @JsonProperty("mrg_stas")
    private String mrgStas;

    @JsonProperty("curr_addr")
    private String currAddr;

    @JsonProperty("psn_tel")
    private String psnTel;

    @JsonProperty("coner_name")
    private String conerName;

    @JsonProperty("coner_tel")
    private String conerTel;

    @JsonProperty("adm_way_code")
    private String admWayCode;

    @JsonProperty("adm_caty")
    private String admCaty;

    @JsonProperty("dscg_caty")
    private String dscgCaty;

    @JsonProperty("adm_date")
    private String admDate;

    @JsonProperty("dscg_date")
    private String dscgDate;

    @JsonProperty("ipt_days")
    private String iptDays;

    @JsonProperty("dscg_way")
    private String dscgWay;

    @JsonProperty("deptdrt_name")
    private String deptdrtName;

    @JsonProperty("chfdr_name")
    private String chfdrName;

    @JsonProperty("atddr_name")
    private String atddrName;

    @JsonProperty("ipt_dr_name")
    private String iptDrName;

    @JsonProperty("resp_nurs_name")
    private String respNursName;

    @JsonProperty("medfee_sumamt")
    private String medfeeSumamt;

    @JsonProperty("selfpay_amt")
    private String selfpayAmt;

    @JsonProperty("medfee_paymtd_code")
    private String medfeePaymtdCode;

    @JsonProperty("fixmedins_code")
    private String fixmedinsCode;

    @JsonProperty("vali_flag")
    private String valiFlag;
}
