/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.documentdetail.request;

import com.openhis.whale.core.application.dto.ExcelExportColumnDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 单据详情导出入参（IF-INV-DDC-{域}-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "单据详情导出 IF-INV-DDC-04")
public class ExportDocumentDetailsRequest extends DocumentDetailFilterRequest {

    /**
     * 前端下发的导出列；空则导出全部动态列。
     */
    @Schema(description = "导出列清单（页面可见列）")
    private List<ExcelExportColumnDto> exportColumns;
}
