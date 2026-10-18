/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.deptdesk.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 批量保存小项结果响应。
 */
@Data
public class BatchSaveObservationsResponse {

    /** 成功条数。 */
    @Schema(description = "成功条数")
    private int successCount;

    /** 各条保存结果。 */
    @Schema(description = "各条保存结果")
    private List<SaveObservationResponse> results;
}
