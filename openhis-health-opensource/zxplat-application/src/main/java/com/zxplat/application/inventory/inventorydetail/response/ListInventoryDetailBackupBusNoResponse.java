/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.inventorydetail.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 备份单号下拉出参（IF-INV-DTL-11）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "备份单号列表 IF-INV-DTL-11")
public class ListInventoryDetailBackupBusNoResponse {
    /** 备份批次单号列表（降序）。 */
    @Schema(description = "备份批次单号列表（降序）")
    private List<String> backupBusNoList;
}
