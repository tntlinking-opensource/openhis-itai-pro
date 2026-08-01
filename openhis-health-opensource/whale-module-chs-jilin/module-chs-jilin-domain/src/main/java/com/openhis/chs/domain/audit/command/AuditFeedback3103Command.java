/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.audit.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 【3103】事前提醒反馈命令。
 *
 * <p>{@code warns[].warnRsltId} 填明细 {@code jrdId}（产品 R7=B）。</p>
 */
@Data
@Accessors(chain = true)
public class AuditFeedback3103Command {

    /** 契约主键 */
    private Long contractId;
    /** 契约 bus_no */
    private String contractBusNo;
    /** 操作员 ID */
    private Long operatorId;
    /** 提醒类型：1 事前 / 2 事中 */
    private String warnType;
    /** 处置明细集合 */
    private List<AuditFeedbackWarnCommand> warns = new ArrayList<>();
}
