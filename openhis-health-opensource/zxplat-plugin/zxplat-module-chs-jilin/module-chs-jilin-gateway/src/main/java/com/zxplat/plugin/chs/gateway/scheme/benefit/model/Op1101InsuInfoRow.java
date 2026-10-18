/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.benefit.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 【1101】人员信息 output.insuinfo 行 */
@Data
@Accessors(chain = true)
public class Op1101InsuInfoRow {

    @JSONField(name = "insuplc_admdvs")
    private String insuplcAdmdvs;

    private String insutype;

    @JSONField(name = "psn_type")
    private String psnType;

    @JSONField(name = "psn_insu_stas")
    private String psnInsuStas;

    @JSONField(name = "psn_insu_date")
    private String psnInsuDate;

    @JSONField(name = "paus_insu_date")
    private String pausInsuDate;

    @JSONField(name = "cvlserv_flag")
    private String cvlservFlag;

    @JSONField(name = "emp_name")
    private String empName;

    private BigDecimal balc;
}
