/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.audit.response;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 事前提醒统计明细行。
 */
@Getter
@Setter
@Accessors(chain = true)
public class DetailAuditStatsLineRow {

    /** 违规明细标识 jrd_id（3103 warn_rslt_id） */
    private String jrdId;
    /** 违规头 jr_id */
    private String jrId;
    /** 处方/医嘱 ID */
    private String rxId;
    /** 违规项目类型 */
    private String volaItemType;
    /** 明细违规金额 */
    private BigDecimal volaAmt;
    /** 医保就诊 ID */
    private String mdtrtId;
}
