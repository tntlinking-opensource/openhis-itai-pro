/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.inventorydetail.request;

import com.openhis.whale.core.application.dto.ExcelExportColumnDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 备份库存导出入参（IF-INV-DTL-07）；筛选与备份汇总同构。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "备份库存明细导出 IF-INV-DTL-07")
public class ExportInventoryDetailBackupRequest extends SumInventoryDetailBackupRequest {

    /**
     * 前端下发的导出列；空则导出全部 {@code @Excel} 列。
     */
    @Schema(description = "导出列清单（页面可见列）")
    private List<ExcelExportColumnDto> exportColumns;
}
