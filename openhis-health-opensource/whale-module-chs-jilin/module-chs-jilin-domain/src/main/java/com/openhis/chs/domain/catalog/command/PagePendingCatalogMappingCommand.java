/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.command;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 医保目录对码 Workbench 待上已对码分页查询 */
@Data
@Accessors(chain = true)
@Schema(description = "待对码分页查询")
public class PagePendingCatalogMappingCommand {

    @Schema(description = "契约 bus_no（批量上传时必填）")
    private String contractBusNo;

    @Schema(description = "产品表：med_medication_definition / wkf_activity_definition / adm_device_definition；空=全部")
    private String productTable;

    @Schema(description = "对码状态：PENDING_UPLOAD（默认）/ MATCHED / NO_YB_NO")
    private String mappingStatus;

    @Schema(description = "名称/编码关键词")
    private String keyword;

    @Schema(description = "页码，从 1 开始")
    private Integer pageNo;

    @Schema(description = "每页条数")
    private Integer pageSize;
}
