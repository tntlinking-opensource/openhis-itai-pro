/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.audit.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 3101 医嘱/费用节点（fsi_order_dtos 元素） */
@Data
@Accessors(chain = true)
public class AuditOrderCommand {

    /** 处方/医嘱明细 ID（违规回传 rx_id） */
    private String rxId;
    /** 处方号 */
    private String rxno;
    /** 组号 */
    private String grpno;
    /** 长期医嘱标志 */
    private String longDrordFlag;
    /** 目录类别 */
    private String hilistType;
    /** 收费类别 */
    private String chrgType;
    /** 医嘱行为 */
    private String drordBhvr;
    /** 医保目录编码 */
    private String hilistCode;
    /** 医保目录名称 */
    private String hilistName;
    /** 剂型 */
    private String hilistDosform;
    /** 医保目录等级 */
    private String hilistLv;
    /** 目录单价 */
    private BigDecimal hilistPric;
    /** 医院目录编码 */
    private String hosplistCode;
    /** 医院目录名称 */
    private String hosplistName;
    /** 医院剂型 */
    private String hosplistDosform;
    /** 数量 */
    private BigDecimal cnt;
    /** 单价 */
    private BigDecimal pric;
    /** 金额 */
    private BigDecimal sumamt;
    /** 全自费 */
    private BigDecimal ownpayAmt;
    /** 自付 */
    private BigDecimal selfpayAmt;
    /** 规格 */
    private String spec;
    /** 规格单位 */
    private String specUnt;
    /** 医嘱开始时间 */
    private String drordBegnDate;
    /** 医嘱停止时间 */
    private String drordStopDate;
    /** 开单科室编码 */
    private String drordDeptCodg;
    /** 开单科室名称 */
    private String drordDeptName;
    /** 开单医师编码 */
    private String drordDrCodg;
    /** 开单医师姓名 */
    private String drordDrName;
    /** 开单医师职称 */
    private String drordDrProfttl;
    /** 当前医嘱标志 */
    private String currDrordFlag;
}
