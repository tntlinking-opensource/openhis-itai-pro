/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 单条药品 3302 撤销（bridge 编排：加载院内药品 → 3302 → 回写 yb_match_flag）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "药品 3302 撤销")
public class RevokeMedicationCatalog3302Request {

    @Schema(description = "契约 bus_no", requiredMode = Schema.RequiredMode.REQUIRED)
    private String contractBusNo;

    @Schema(description = "药品定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long medicationDefId;

    @Schema(description = "list_type，空则 101")
    private String listType;
}
