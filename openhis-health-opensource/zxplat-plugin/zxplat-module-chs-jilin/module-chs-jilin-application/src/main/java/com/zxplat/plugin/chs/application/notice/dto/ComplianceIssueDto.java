/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.dto;

import lombok.Data;

/**
 * 合规问题行（R-03 issues 元素）。
 */
@Data
public class ComplianceIssueDto {
    /** 门禁/问题码（如 C-STOP-LAG、C-PAY-CLASS）。 */
    private String code;
    /** 国家目录编码。 */
    private String hilistCode;
    /** 物价/项目代码（可空）。 */
    private String projectCode;
    /** 对人可读说明。 */
    private String message;
    /** 严重度（HIGH / WARN）。 */
    private String severity;
}
