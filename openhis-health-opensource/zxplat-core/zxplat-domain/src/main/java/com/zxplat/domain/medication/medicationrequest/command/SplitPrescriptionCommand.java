/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门诊药品处方分方命令（应用层预装配行快照与金额阈值，领域层只执行装箱与回写）。
 */
@Getter
@Setter
public class SplitPrescriptionCommand {

    /** 操作人标识。 */
    private Long operatorId;

    /** 就诊标识。 */
    private Long encounterId;

    /** 待分方行快照（须覆盖本次就诊全部未收费药品）。 */
    private List<PrescriptionSplitLineContext> lines;

    /** 单张处方金额上限（元）；由系统设置注入。 */
    private BigDecimal amountLimitYuan;
}
