/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.inventorydetail.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 备份单号下拉入参（IF-INV-DTL-11）；当前无业务字段，租户由 Gate 注入。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "备份单号列表 IF-INV-DTL-11")
public class ListInventoryDetailBackupBusNoRequest {
}
