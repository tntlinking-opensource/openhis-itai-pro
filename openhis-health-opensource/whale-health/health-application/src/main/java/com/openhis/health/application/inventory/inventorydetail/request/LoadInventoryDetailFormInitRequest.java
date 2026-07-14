/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.inventorydetail.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 库存明细页初始化入参（IF-INV-DTL-01）；当前无业务字段，租户由会话解析。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库存明细页初始化 IF-INV-DTL-01")
public class LoadInventoryDetailFormInitRequest {
}
