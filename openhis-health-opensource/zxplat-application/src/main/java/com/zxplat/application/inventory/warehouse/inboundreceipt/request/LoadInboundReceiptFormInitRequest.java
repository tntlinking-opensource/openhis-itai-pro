/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.inboundreceipt.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 药库入库页初始化请求（IF-WHS-IN-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药库入库页初始化请求 IF-WHS-IN-01")
public class LoadInboundReceiptFormInitRequest {
}
