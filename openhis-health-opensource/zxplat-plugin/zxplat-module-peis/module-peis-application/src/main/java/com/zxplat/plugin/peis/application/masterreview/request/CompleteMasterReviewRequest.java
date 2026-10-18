/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.masterreview.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 总检完成请求（IF-PEIS-MAS-04）。
 */
@Data
public class CompleteMasterReviewRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 就诊主键。 */
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;
}
