/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.audit.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 明细审核违规头（对应 3101 {@code output.result[]} 一行）。
 *
 * <p>完整报文见 {@code yb_invoke_log}；本表仅结构化摘要供统计/试调回看。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "yb_detail_audit_hit")
public class YbDetailAuditHit extends BaseAuditedEntity {

    /** 违规结果 ID（平台 jr_id，租户内唯一） */
    @Column(name = "jr_id", nullable = false, length = 64)
    private String jrId;

    /** 任务 ID */
    @Column(name = "task_id", length = 64)
    private String taskId;

    /** 触发场景 */
    @Column(name = "trig_scen", length = 8)
    private String trigScen;

    /** 契约 bus_no */
    @Column(name = "contract_bus_no", length = 64)
    private String contractBusNo;

    /** 医保就诊 ID */
    @Column(name = "mdtrt_id", length = 64)
    private String mdtrtId;

    /** 参保人 ID */
    @Column(name = "patn_id", length = 64)
    private String patnId;

    /** 规则 ID */
    @Column(name = "rule_id", length = 64)
    private String ruleId;

    /** 规则名称 */
    @Column(name = "rule_name", length = 256)
    private String ruleName;

    /** 违规内容 */
    @Column(name = "vola_cont", length = 2000)
    private String volaCont;

    /** 违规金额 */
    @Column(name = "vola_amt", precision = 18, scale = 4)
    private BigDecimal volaAmt;

    /** 严重程度 */
    @Column(name = "sev_deg", length = 8)
    private String sevDeg;

    /** 违规行为分类 */
    @Column(name = "vola_bhvr_type", length = 8)
    private String volaBhvrType;

    /** 开单/责任医师编码（统计页筛选用） */
    @Column(name = "dr_codg", length = 64)
    private String drCodg;

    /** 开单/责任医师姓名 */
    @Column(name = "dr_name", length = 128)
    private String drName;

    /** 科室编码 */
    @Column(name = "dept_codg", length = 64)
    private String deptCodg;

    /** 科室名称 */
    @Column(name = "dept_name", length = 128)
    private String deptName;

    /** 操作员 ID */
    @Column(name = "operator_id")
    private Long operatorId;
}
