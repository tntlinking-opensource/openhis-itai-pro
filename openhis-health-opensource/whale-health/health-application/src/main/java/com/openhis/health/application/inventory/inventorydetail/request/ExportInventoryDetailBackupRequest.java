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
 * 备份库存导出入参（IF-INV-DTL-07）；筛选与备份汇总同构。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "备份库存明细导出 IF-INV-DTL-07")
public class ExportInventoryDetailBackupRequest extends SumInventoryDetailBackupRequest {
}
