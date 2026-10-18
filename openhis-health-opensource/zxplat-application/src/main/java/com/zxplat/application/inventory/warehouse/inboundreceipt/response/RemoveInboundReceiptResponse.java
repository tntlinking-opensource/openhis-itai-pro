/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.inboundreceipt.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 删除药库入库单响应（IF-WHS-IN-10）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "删除药库入库单 IF-WHS-IN-10")
public class RemoveInboundReceiptResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;
}
