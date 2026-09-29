/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.feelist.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 打印医技费用清单响应（AS-MT-FEE-001 IF-MT-FEE-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "打印医技费用清单响应")
public class PrintMedicalTechFeeListResponse {

    /** 打印追踪号（preparePrint 返回）。 */
    @Schema(description = "打印追踪号")
    private String traceId;

    /** 提示文案。 */
    @Schema(description = "提示")
    private String message;

    /** 是否成功触发 prepare。 */
    @Schema(description = "是否成功")
    private Boolean succeeded;
}
