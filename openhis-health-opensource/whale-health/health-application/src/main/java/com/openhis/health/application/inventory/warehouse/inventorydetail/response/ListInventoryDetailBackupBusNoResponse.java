/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inventory.warehouse.inventorydetail.response;

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

    /** 本租户历史备份批次单号，按 bus_no 降序。 */
    @Schema(description = "备份批次单号列表（降序）")
    private List<String> backupBusNoList;
}
