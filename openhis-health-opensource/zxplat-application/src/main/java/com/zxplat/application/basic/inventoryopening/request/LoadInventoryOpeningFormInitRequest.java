/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.inventoryopening.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 进入库存期初页初始化请求（IF-BAS-INV-OPN-01）；可空体。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库存期初页初始化")
public class LoadInventoryOpeningFormInitRequest {
}
