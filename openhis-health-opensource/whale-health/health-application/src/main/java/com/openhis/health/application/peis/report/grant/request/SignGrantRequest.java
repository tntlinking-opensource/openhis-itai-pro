/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.report.grant.request;

import lombok.Data;

/**
 * 签收请求（IF-PEIS-REP-05）。
 */
@Data
public class SignGrantRequest {

    /** 总检报告主键。 */
    private Long diagnosticReportId;

    /** 签收人姓名。 */
    private String recipientName;

    /** 操作人标识。 */
    private Long operatorId;
}
