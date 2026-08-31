/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 住院医保费用明细行（2301） */
@Data
@Accessors(chain = true)
public class IpSettleChargeLine {

    private Long chargeItemId;
    private String feedetlSn;
    /**
     * 原费用流水号（2301 {@code init_feedetl_sn}）；
     * 冲销行（{@code ChargeItem.refundId}）必填，指向被退正数行流水。
     */
    private String initFeedetlSn;
    private String medListCodg;
    private String medinsListCodg;
    /**
     * 医院目录/项目名称（3101 {@code hilist_name}/{@code hosplist_name}；2301 可忽略）。
     */
    private String itemName;
    private BigDecimal detItemFeeSumamt;
    private BigDecimal cnt;
    private BigDecimal pric;
    private LocalDateTime feeOcurTime;
    private String drordNo;
    private String bilgDeptCodg;
    private String bilgDeptName;
    private String bilgDrCodg;
    private String bilgDrName;
}
