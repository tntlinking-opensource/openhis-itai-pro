/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.gainloss.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 审批驳回药库损益单响应（IF-WHS-GL-09）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "审批驳回药库损益 IF-WHS-GL-09")
public class RejectGainLossResponse {

    /** 是否成功 success。 */
    @Schema(description = "是否成功 success")
    private Boolean success;
}
