/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.model;

import lombok.Data;

/**
 * 合规问题领域视图。
 */
@Data
public class ComplianceIssueView {
    /** 门禁/问题码（见 {@code CatalogNoticeComplianceGate} 常量）。 */
    private String code;
    /** 国家目录编码。 */
    private String hilistCode;
    /** 物价/项目代码；可空。 */
    private String projectCode;
    /** 说明文案。 */
    private String message;
    /** 严重度（HIGH / WARN）。 */
    private String severity;
}
