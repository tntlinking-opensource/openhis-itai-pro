/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.qc.model;

import com.openhis.health.domain.mrms.qc.entity.QcDefect;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次质控执行结果（应用层 {@code QcRuleExecutor} 回传，非持久化实体）。
 */
@Getter
@Setter
public class QcRunResult {

    /** 质控执行批次主键 {@code mrms_qc_run.id}。 */
    private Long qcRunId;
    /** 是否通过（无 open 阻断级缺陷）。 */
    private boolean passed;
    /** 本次执行 open 阻断级缺陷数（归档门禁用）。 */
    private int openBlockCount;
    /** 本次执行 open 警告级缺陷数。 */
    private int openWarnCount;
    /** 本轮 upsert 后的缺陷实体列表（供应用层展示）。 */
    private List<QcDefect> defects = new ArrayList<>();
}
