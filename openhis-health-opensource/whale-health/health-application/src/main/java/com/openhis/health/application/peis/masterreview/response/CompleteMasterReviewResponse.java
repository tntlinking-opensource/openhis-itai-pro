/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.masterreview.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 总检完成响应（IF-PEIS-MAS-04）。
 */
@Data
public class CompleteMasterReviewResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 就诊主键。 */
    private Long encounterId;

    /** 报告主键。 */
    private Long diagnosticReportId;

    /** 报告状态。 */
    private String statusEnum;
}
