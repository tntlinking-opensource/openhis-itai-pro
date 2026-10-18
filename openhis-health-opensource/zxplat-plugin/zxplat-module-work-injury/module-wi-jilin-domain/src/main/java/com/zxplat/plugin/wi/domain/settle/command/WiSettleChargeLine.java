/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.settle.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工伤 2204 处方明细行（HIS → 平台 inputs[]）。
 */
@Data
@Accessors(chain = true)
public class WiSettleChargeLine {

    /** HIS 收费项 id（本地关联，不上报平台）。 */
    private Long chargeItemId;

    /** 处方流水号 feedetl_sn。 */
    private String feedetlSn;

    /** 处方号 rxno。 */
    private String rxno;

    /** 三大目录类别 list_type。 */
    private String listType;

    /** 中心编码 med_list_codg。 */
    private String medListCodg;

    private BigDecimal pric;

    private BigDecimal cnt;

    /** 总金额 umamt（单价×数量四舍五入两位）。 */
    private BigDecimal umamt;

    /** 费用发生时间 fee_ocur_time。 */
    private LocalDateTime feeOcurTime;

    /** 开单医生编码 bilg_dr_codg。 */
    private String bilgDrCodg;

    /** 开单科室编码 bilg_dept_codg。 */
    private String bilgDeptCodg;
}
