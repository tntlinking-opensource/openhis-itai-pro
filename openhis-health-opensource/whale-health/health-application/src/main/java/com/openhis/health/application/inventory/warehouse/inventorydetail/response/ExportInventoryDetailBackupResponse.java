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

/**
 * 备份库存导出 Excel 出参（IF-INV-DTL-07）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "备份库存明细导出 IF-INV-DTL-07")
public class ExportInventoryDetailBackupResponse {

    /** 下载文件名。 */
    @Schema(description = "下载文件名")
    private String fileName;

    /** 内容类型。 */
    @Schema(description = "内容类型")
    private String contentType;

    /** 工作簿字节流。 */
    @Schema(description = "工作簿字节流")
    private byte[] body;

    /** 本次导出写入的数据行数。 */
    @Schema(description = "导出数据行数")
    private Integer exportedRowCount;

    /** 是否因达到单次导出上限而截断。 */
    @Schema(description = "是否因行数上限截断")
    private Boolean truncated;
}
