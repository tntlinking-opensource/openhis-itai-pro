/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.audit.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 3101 fsi_order_dtos 元素 */
@Data
@Accessors(chain = true)
public class Audit3101OrderRow {

    @JSONField(name = "rx_id")
    private String rxId;

    private String rxno;

    private String grpno;

    @JSONField(name = "long_drord_flag")
    private String longDrordFlag;

    @JSONField(name = "hilist_type")
    private String hilistType;

    @JSONField(name = "chrg_type")
    private String chrgType;

    @JSONField(name = "drord_bhvr")
    private String drordBhvr;

    @JSONField(name = "hilist_code")
    private String hilistCode;

    @JSONField(name = "hilist_name")
    private String hilistName;

    @JSONField(name = "hilist_dosform")
    private String hilistDosform;

    @JSONField(name = "hilist_lv")
    private String hilistLv;

    @JSONField(name = "hilist_pric")
    private BigDecimal hilistPric;

    @JSONField(name = "hosplist_code")
    private String hosplistCode;

    @JSONField(name = "hosplist_name")
    private String hosplistName;

    @JSONField(name = "hosplist_dosform")
    private String hosplistDosform;

    private BigDecimal cnt;

    private BigDecimal pric;

    private BigDecimal sumamt;

    @JSONField(name = "ownpay_amt")
    private BigDecimal ownpayAmt;

    @JSONField(name = "selfpay_amt")
    private BigDecimal selfpayAmt;

    private String spec;

    @JSONField(name = "spec_unt")
    private String specUnt;

    @JSONField(name = "drord_begn_date")
    private String drordBegnDate;

    @JSONField(name = "drord_stop_date")
    private String drordStopDate;

    @JSONField(name = "drord_dept_codg")
    private String drordDeptCodg;

    @JSONField(name = "drord_dept_name")
    private String drordDeptName;

    @JSONField(name = "drord_dr_codg")
    private String drordDrCodg;

    @JSONField(name = "drord_dr_name")
    private String drordDrName;

    @JSONField(name = "drord_dr_profttl")
    private String drordDrProfttl;

    @JSONField(name = "curr_drord_flag")
    private String currDrordFlag;
}
