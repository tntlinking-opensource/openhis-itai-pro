/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.command;

import lombok.Data;

/**
 * 跑合规校验写命令（W-05）。
 */
@Data
public class RunComplianceCommand {
    /** 租户主键（必填）。 */
    private Long tenantId;
    /** 契约过滤；可空。 */
    private Long contractId;
    /** 目录类别过滤；可空。 */
    private String listType;
    /** 操作者；写入报告 ran_by。 */
    private Long operatorId;
}
