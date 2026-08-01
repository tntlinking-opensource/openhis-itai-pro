/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.audit.response;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 事前提醒统计主表行。
 */
@Getter
@Setter
@Accessors(chain = true)
public class DetailAuditStatsHitRow {

    /** 违规标识 jr_id */
    private String jrId;
    /** 3101 落库时间 */
    private LocalDateTime createdAt;
    /** 责任医师编码 */
    private String drCodg;
    /** 责任医师姓名 */
    private String drName;
    /** 科室编码 */
    private String deptCodg;
    /** 科室名称 */
    private String deptName;
    /** 医保就诊 ID */
    private String mdtrtId;
    /** 参保人标识 */
    private String patnId;
    /** 规则 ID */
    private String ruleId;
    /** 规则名称 */
    private String ruleName;
    /** 严重程度 */
    private String sevDeg;
    /** 违规内容摘要 */
    private String volaCont;
    /** 违规金额 */
    private BigDecimal volaAmt;
    /** 触发场景 */
    private String trigScen;
    /** 契约 bus_no */
    private String contractBusNo;
    /** 末次 3103 处置；空=尚未反馈 */
    private String latestDspoWay;
}
