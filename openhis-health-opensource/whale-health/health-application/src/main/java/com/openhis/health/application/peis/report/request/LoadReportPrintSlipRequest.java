/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.report.request;

import lombok.Data;

/**
 * 加载体检报告打印 slip 请求（IF-PEIS-REP-02 扩展；真打印前拉数）。
 */
@Data
public class LoadReportPrintSlipRequest {

    /** 就诊标识。 */
    private Long encounterId;
}
