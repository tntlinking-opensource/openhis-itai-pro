/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.report.request;

import lombok.Data;

/**
 * 确认打印请求（IF-PEIS-REP-03）。
 */
@Data
public class ConfirmPrintRequest {

    /** 就诊标识。 */
    private Long encounterId;
}
