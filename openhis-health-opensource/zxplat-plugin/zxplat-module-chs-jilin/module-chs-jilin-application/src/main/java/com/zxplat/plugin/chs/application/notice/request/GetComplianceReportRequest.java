/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.request;

import lombok.Data;

/**
 * 查询最新合规报告请求（IF-CHS-CAT-005-R-03）。
 */
@Data
public class GetComplianceReportRequest {
    /** 契约主键过滤；空表示不限契约。 */
    private Long contractId;
    /** 目录类别过滤；空表示不限。 */
    private String listType;
}
