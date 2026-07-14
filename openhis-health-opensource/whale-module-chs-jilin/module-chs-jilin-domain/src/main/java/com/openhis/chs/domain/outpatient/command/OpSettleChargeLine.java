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
}
