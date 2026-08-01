/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.procedurecatalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 国家临床版手术 3.0 导入结果。
 */
@Data
@Schema(description = "国家临床版手术3.0导入结果")
public class ImportClinicalProcV3CatalogResponse {

    @Schema(description = "解析到的有效行数")
    private int totalCount;

    @Schema(description = "写入（新增+更新）条数")
    private int successCount;
}
