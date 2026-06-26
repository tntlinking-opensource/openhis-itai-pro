/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Workbench 行：院内产品 + 医保对码字段。
 */
@Data
@Accessors(chain = true)
@Schema(description = "待对码行")
public class PendingCatalogMappingRowDto {

    private String productTable;
    private Long productId;
    private String productTypeLabel;
    private String busNo;
    private String name;
    private String ybNo;
    private Boolean ybMatchFlag;
    private Integer chrgitmLv;
    /** 3301 list_type */
    private String listType;
}
