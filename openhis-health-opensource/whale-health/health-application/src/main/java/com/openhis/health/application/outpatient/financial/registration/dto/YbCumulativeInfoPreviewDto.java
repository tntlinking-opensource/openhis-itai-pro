/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 5206 累计信息预览行。 */
@Data
@Accessors(chain = true)
public class YbCumulativeInfoPreviewDto {

    private String insutype;
    private String year;
    private String cumYm;
    private String cumTypeCode;
    private String cum;
}
