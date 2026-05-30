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

package com.openhis.health.application.basic.medicationcatalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 药品目录批量导入结果摘要（领域导入完成后回写统计）。
 */
@Data
@Schema(description = "药品目录导入结果")
public class ImportMedicationCatalogResponse {
    /** 批次号，对应一次上传追踪。 */
    @Schema(description = "导入批次号", example = "MED-IMPORT-1a2b3c4d")
    private String batchNo;

    /** 解析得到的业务数据行数（不含表头）。 */
    @Schema(description = "总行数（不含表头）", example = "20")
    private Integer totalCount;

    /** 领域报告成功落库条数。 */
    @Schema(description = "成功条数", example = "18")
    private Integer successCount;

    /** 领域报告失败条数。 */
    @Schema(description = "失败条数", example = "2")
    private Integer failCount;
}
