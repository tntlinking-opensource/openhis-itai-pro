/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.report.grant.request;

import lombok.Data;

/**
 * 发放登记请求（IF-PEIS-REP-05）。
 */
@Data
public class RegisterGrantRequest {

    /** 总检报告主键。 */
    private Long diagnosticReportId;

    /** 操作人标识（可选，回落登录人）。 */
    private Long operatorId;
}
