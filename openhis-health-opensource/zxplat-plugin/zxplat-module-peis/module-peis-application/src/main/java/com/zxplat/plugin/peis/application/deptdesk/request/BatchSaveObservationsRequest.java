/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.deptdesk.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 批量保存小项结果请求。
 */
@Data
public class BatchSaveObservationsRequest {

    /** 待保存小项列表（顺序提交，同一事务）。 */
    @Schema(description = "待保存小项列表")
    private List<SaveObservationRequest> items;
}
