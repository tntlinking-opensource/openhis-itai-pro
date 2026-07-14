/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.inventory.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/** 进销存销售 selinfo 节点（3505/3506 共用） */
@Data
@Accessors(chain = true)
public class InvSelinfo {

    @JSONField(name = "med_list_codg")
    private String medListCodg;

    @JSONField(name = "fixmedins_hilist_id")
    private String fixmedinsHilistId;

    @JSONField(name = "fixmedins_hilist_name")
    private String fixmedinsHilistName;

    @JSONField(name = "fixmedins_bchno")
    private String fixmedinsBchno;

    @JSONField(name = "prsc_dr_name")
    private String prscDrName;

    @JSONField(name = "phar_name")
    private String pharName;

    @JSONField(name = "phar_prac_cert_no")
    private String pharPracCertNo;

    @JSONField(name = "hi_feesetl_type")
    private String hiFeesetlType;

    @JSONField(name = "setl_id")
    private String setlId;

    @JSONField(name = "mdtrt_sn")
    private String mdtrtSn;

    @JSONField(name = "manu_lotnum")
    private String manuLotnum;

    @JSONField(name = "manu_date")
    private String manuDate;

    @JSONField(name = "expy_end")
    private String expyEnd;

    @JSONField(name = "rx_flag")
    private String rxFlag;

    @JSONField(name = "trdn_flag")
    private String trdnFlag;

    @JSONField(name = "finl_trns_pric")
    private BigDecimal finlTrnsPric;

    @JSONField(name = "rtal_docno")
    private String rtalDocno;

    @JSONField(name = "sel_retn_cnt")
    private BigDecimal selRetnCnt;

    @JSONField(name = "sel_retn_time")
    private String selRetnTime;

    @JSONField(name = "sel_retn_opter_name")
    private String selRetnOpterName;

    @JSONField(name = "mdtrt_setl_type")
    private String mdtrtSetlType;

    @JSONField(name = "psn_no")
    private String psnNo;

    @JSONField(name = "psn_cert_type")
    private String psnCertType;

    private String certno;

    @JSONField(name = "psn_name")
    private String psnName;

    @JSONField(name = "medins_prod_sel_no")
    private String medinsProdSelNo;

    private List<InvDrugtracinfo> drugtracinfo;
}
