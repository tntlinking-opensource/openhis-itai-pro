/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.audit.snapshot;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** Snapshot 费用/医嘱行（与 OpSettleChargeLine 语义对齐） */
@Data
@Accessors(chain = true)
public class AuditFeeOrderLine {

    /** 处方/费用明细 ID */
    private String rxId;
    /** 处方号 */
    private String rxno;
    /** 医保目录编码 */
    private String hilistCode;
    /** 医院目录编码 */
    private String hosplistCode;
    /** 项目名称 */
    private String hilistName;
    /** 数量 */
    private BigDecimal cnt;
    /** 单价 */
    private BigDecimal pric;
    /** 金额 */
    private BigDecimal sumamt;
    /** 开单科室编码 */
    private String drordDeptCodg;
    /** 开单科室名称 */
    private String drordDeptName;
    /** 开单医师编码 */
    private String drordDrCodg;
    /** 开单医师姓名 */
    private String drordDrName;
    /** 医嘱开始时间 */
    private String drordBegnDate;
    /** 目录类别（可空） */
    private String hilistType;
    /** 收费类别（可空） */
    private String chrgType;
    /** 目录等级（可空） */
    private String hilistLv;
    /** 规格 */
    private String spec;
    /** 规格单位 */
    private String specUnt;
}
