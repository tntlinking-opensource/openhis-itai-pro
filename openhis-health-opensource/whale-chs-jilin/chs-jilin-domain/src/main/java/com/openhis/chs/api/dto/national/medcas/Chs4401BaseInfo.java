package com.openhis.chs.api.dto.national.medcas;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * 4401 baseinfo 节点（首批实现字段）。
 */
@Data
public class Chs4401BaseInfo {

    @JSONField(name = "mdtrt_sn")
    private String mdtrtSn;

    @JSONField(name = "mdtrt_id")
    private String mdtrtId;

    @JSONField(name = "psn_no")
    private String psnNo;

    @JSONField(name = "patn_ipt_cnt")
    private String patnIptCnt;

    @JSONField(name = "ipt_no")
    private String iptNo;

    private String medcasno;

    @JSONField(name = "psn_name")
    private String psnName;

    private String gend;

    private String brdy;

    private String ntly;

    private String naty;

    private String certno;

    private String prfs;

    @JSONField(name = "mrg_stas")
    private String mrgStas;

    @JSONField(name = "curr_addr")
    private String currAddr;

    @JSONField(name = "psn_tel")
    private String psnTel;

    @JSONField(name = "coner_name")
    private String conerName;

    @JSONField(name = "coner_tel")
    private String conerTel;

    @JSONField(name = "adm_way_code")
    private String admWayCode;

    @JSONField(name = "adm_caty")
    private String admCaty;

    @JSONField(name = "dscg_caty")
    private String dscgCaty;

    @JSONField(name = "adm_date")
    private String admDate;

    @JSONField(name = "dscg_date")
    private String dscgDate;

    @JSONField(name = "ipt_days")
    private String iptDays;

    @JSONField(name = "dscg_way")
    private String dscgWay;

    @JSONField(name = "deptdrt_name")
    private String deptdrtName;

    @JSONField(name = "chfdr_name")
    private String chfdrName;

    @JSONField(name = "atddr_name")
    private String atddrName;

    @JSONField(name = "ipt_dr_name")
    private String iptDrName;

    @JSONField(name = "resp_nurs_name")
    private String respNursName;

    @JSONField(name = "medfee_sumamt")
    private String medfeeSumamt;

    @JSONField(name = "selfpay_amt")
    private String selfpayAmt;

    @JSONField(name = "medfee_paymtd_code")
    private String medfeePaymtdCode;

    @JSONField(name = "fixmedins_code")
    private String fixmedinsCode;

    @JSONField(name = "vali_flag")
    private String valiFlag;
}
