/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 项目 pending 互斥查询响应（IF-PRC-12）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "项目 pending 互斥查询响应")
public class ExistsPendingByItemResponse {

    /** 是否存在待审批调价单。 */
    @Schema(description = "是否存在待审批调价单")
    private Boolean existsPending;
}
