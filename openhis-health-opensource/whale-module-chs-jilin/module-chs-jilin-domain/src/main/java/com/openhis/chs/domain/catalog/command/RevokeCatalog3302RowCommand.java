/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.command;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 3302 单行撤销 */
@Data
@Accessors(chain = true)
@Schema(description = "3302 单行")
public class RevokeCatalog3302RowCommand {

    @Schema(description = "产品表名，如 med_medication_definition")
    private String productTable;

    @Schema(description = "产品主键")
    private Long productId;

    @Schema(description = "定点机构目录编号 fixmedins_hilist_id")
    private String fixmedinsHilistId;

    @Schema(description = "list_type")
    private String listType;

    @Schema(description = "医疗目录编码 med_list_codg")
    private String medListCodg;
}
