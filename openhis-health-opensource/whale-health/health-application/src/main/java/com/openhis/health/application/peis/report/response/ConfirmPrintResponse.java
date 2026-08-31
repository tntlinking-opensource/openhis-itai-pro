/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.report.response;

import lombok.Data;

/**
 * 确认打印响应（IF-PEIS-REP-03）。
 */
@Data
public class ConfirmPrintResponse {

    /** 是否受理。 */
    private Boolean accepted;

    /** 提示信息。 */
    private String message;

    /** 总检报告主键。 */
    private Long diagnosticReportId;

    /** 累计打印次数。 */
    private Integer printCount;
}
