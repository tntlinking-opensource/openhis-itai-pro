/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.audit.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 【3101】事前分析结果。
 *
 * <p>无违规时 {@code violations} 为空列表（非 null）。</p>
 */
@Data
@Accessors(chain = true)
public class AuditAnalyzeResult {

    /** 任务 ID（回显） */
    private String taskId;
    /** 触发场景（回显，便于落库） */
    private String trigScen;
    /** 契约 bus_no（回显） */
    private String contractBusNo;
    /** 违规头列表 */
    private List<AuditViolationResult> violations = new ArrayList<>();
}
