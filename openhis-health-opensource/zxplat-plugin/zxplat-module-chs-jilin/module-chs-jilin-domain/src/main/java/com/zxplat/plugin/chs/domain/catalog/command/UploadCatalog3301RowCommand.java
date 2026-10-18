/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.command;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 3301 单行对照 */
@Data
@Accessors(chain = true)
@Schema(description = "3301 单行")
public class UploadCatalog3301RowCommand {

    @Schema(description = "产品表名，如 med_medication_definition")
    private String productTable;

    @Schema(description = "产品主键")
    private Long productId;

    @Schema(description = "定点机构目录编号 fixmedins_hilist_id")
    private String fixmedinsHilistId;

    @Schema(description = "定点机构目录名称")
    private String fixmedinsHilistName;

    @Schema(description = "list_type")
    private String listType;

    @Schema(description = "医疗目录编码 med_list_codg")
    private String medListCodg;

    @Schema(description = "批准文号")
    private String aprvno;

    @Schema(description = "规格")
    private String spec;

    @Schema(description = "剂型")
    private String dosform;

    @Schema(description = "开始日yyyy-MM-dd")
    private String begndate;

    @Schema(description = "结束日期 yyyy-MM-dd")
    private String enddate;
}
