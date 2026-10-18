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
 * 新建药库入库单响应（IF-WHS-IN-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "新建药库入库单 IF-WHS-IN-04")
public class CreateInboundReceiptResponse {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;
}
