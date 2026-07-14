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
 * 工作台库存/效期预警摘要入参（IF-INV-DTL-12）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "工作台库存预警摘要 IF-INV-DTL-12")
public class LoadInventoryWarningWorkbenchSummaryRequest {
}
