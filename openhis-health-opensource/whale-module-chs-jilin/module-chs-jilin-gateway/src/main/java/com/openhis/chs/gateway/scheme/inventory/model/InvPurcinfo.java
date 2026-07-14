/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.inventory.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 进销存采购 purcinfo 节点（3503/3504 共用） */
@Data
@Accessors(chain = true)
public class InvPurcinfo {

    @JSONField(name = "med_list_codg")
    private String medListCodg;

    @JSONField(name = "fixmedins_hilist_id")
    private String fixmedinsHilistId;

    @JSONField(name = "fixmedins_hilist_name")
    private String fixmedinsHilistName;

    @JSONField(name = "dynt_no")
    private String dyntNo;

    @JSONField(name = "fixmedins_bchno")
    private String fixmedinsBchno;

    @JSONField(name = "spler_name")
    private String splerName;

    @JSONField(name = "spler_pmtno")
    private String splerPmtno;

    @JSONField(name = "manu_lotnum")
    private String manuLotnum;

    @JSONField(name = "prodentp_name")
    private String prodentpName;

    private String aprvno;

    @JSONField(name = "manu_date")
    private String manuDate;

    @JSONField(name = "expy_end")
    private String expyEnd;

    @JSONField(name = "finl_trns_pric")
    private BigDecimal finlTrnsPric;

    @JSONField(name = "purc_retn_cnt")
    private BigDecimal purcRetnCnt;

    @JSONField(name = "purc_invo_codg")
    private String purcInvoCodg;

    @JSONField(name = "purc_invo_no")
    private String purcInvoNo;

    @JSONField(name = "rx_flag")
    private String rxFlag;

    @JSONField(name = "purc_retn_stoin_time")
    private String purcRetnStoinTime;

    @JSONField(name = "purc_retn_opter_name")
    private String purcRetnOpterName;

    @JSONField(name = "prod_geay_flag")
    private String prodGeayFlag;

    private String memo;

    @JSONField(name = "medins_prod_purc_no")
    private String medinsProdPurcNo;
}
