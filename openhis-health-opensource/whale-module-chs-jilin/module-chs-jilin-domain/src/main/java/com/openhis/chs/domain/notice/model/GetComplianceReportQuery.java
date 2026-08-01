/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.model;

import lombok.Data;

/**
 * 查询最新合规报告。
 */
@Data
public class GetComplianceReportQuery {
    /** 租户主键。 */
    private Long tenantId;
    /** 契约过滤；可空。 */
    private Long contractId;
    /** 目录类别过滤；可空。 */
    private String listType;
}
