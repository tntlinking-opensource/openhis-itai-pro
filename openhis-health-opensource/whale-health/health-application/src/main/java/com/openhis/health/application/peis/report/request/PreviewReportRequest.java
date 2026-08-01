/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.report.request;

import lombok.Data;

/**
 * 预览报告请求（IF-PEIS-REP-02）。
 */
@Data
public class PreviewReportRequest {

    /** 就诊标识。 */
    private Long encounterId;
}
