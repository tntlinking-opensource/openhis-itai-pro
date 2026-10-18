/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.outpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 1101 参保信息行 */
@Data
@Accessors(chain = true)
public class YbInsuInfoRow {

    private String insuplcAdmdvs;
    private String insutype;
    private String psnType;
    private String psnInsuStas;
    private String psnInsuDate;
    private String pausInsuDate;
    private String cvlservFlag;
    private String empName;
    private BigDecimal balc;
}
