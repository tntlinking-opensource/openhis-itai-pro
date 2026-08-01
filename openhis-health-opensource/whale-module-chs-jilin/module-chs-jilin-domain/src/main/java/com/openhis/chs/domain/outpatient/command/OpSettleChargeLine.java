/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 2204 单行费用上下文 */
@Data
@Accessors(chain = true)
public class OpSettleChargeLine {

    private Long chargeItemId;
    private String feedetlSn;
    private String medListCodg;
    private String medinsListCodg;
    private BigDecimal detItemFeeSumamt;
    private BigDecimal cnt;
    private BigDecimal pric;
    private LocalDateTime feeOcurTime;
    private String rxno;
    private String diseCodg;
    private String bilgDeptCodg;
    private String bilgDeptName;
    private String bilgDrCodg;
    private String bilgDrName;

    /**
     * 医院目录/项目名称（3101 {@code hilist_name}/{@code hosplist_name}；2204 可忽略）。
     */
    private String itemName;
    /** 目录类别（3101 {@code hilist_type}）。 */
    private String hilistType;
    /** 收费类别（3101 {@code chrg_type}）。 */
    private String chrgType;
    /** 医保目录等级（3101 {@code hilist_lv}）。 */
    private String hilistLv;
    /** 规格（3101 {@code spec}）。 */
    private String spec;
    /** 数量单位（3101 {@code spec_unt}）。 */
    private String specUnt;
}
