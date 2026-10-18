/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.inventory.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/** 进销存盘存/库存变更 invinfo 节点（3501/3502 共用） */
@Data
@Accessors(chain = true)
public class InvInvinfo {

    @JSONField(name = "med_list_codg")
    private String medListCodg;

    @JSONField(name = "inv_chg_type")
    private String invChgType;

    @JSONField(name = "fixmedins_hilist_id")
    private String fixmedinsHilistId;

    @JSONField(name = "fixmedins_hilist_name")
    private String fixmedinsHilistName;

    @JSONField(name = "fixmedins_bchno")
    private String fixmedinsBchno;

    private BigDecimal pric;

    private BigDecimal cnt;

    @JSONField(name = "rx_flag")
    private String rxFlag;

    private String invdate;

    @JSONField(name = "inv_cnt")
    private BigDecimal invCnt;

    @JSONField(name = "manu_lotnum")
    private String manuLotnum;

    @JSONField(name = "manu_date")
    private String manuDate;

    @JSONField(name = "expy_end")
    private String expyEnd;

    private String memo;

    @JSONField(name = "inv_chg_time")
    private String invChgTime;

    @JSONField(name = "inv_chg_opter_name")
    private String invChgOpterName;

    @JSONField(name = "trdn_flag")
    private String trdnFlag;

    private List<InvDrugtracinfo> drugtracinfo;
}
