/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.medicationcatalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 药品目录批量导入结果摘要（领域导入完成后回写统计）。
 */
@Data
@Schema(description = "药品目录导入结果")
public class ImportMedicationCatalogResponse {
    @Schema(description = "导入批次号", example = "MED-IMPORT-1a2b3c4d")
    /** 总行数（不含表头）。 */
    private String batchNo;

    @Schema(description = "总行数（不含表头）", example = "20")
    private Integer totalCount;

    @Schema(description = "成功条数", example = "18")
    /** 成功条数。 */
    private Integer successCount;

    /** 失败条数。 */
    @Schema(description = "失败条数", example = "2")
    private Integer failCount;
}
