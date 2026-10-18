/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.inventorydetail.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 工作台库存/效期预警摘要（IF-INV-DTL-12）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "工作台库存预警摘要 IF-INV-DTL-12")
public class LoadInventoryWarningWorkbenchSummaryResponse {

    /** 是否展示预警入口。 */
    @Schema(description = "是否展示预警入口")
    private Boolean visible;

    /** 效期预警条数。 */
    @Schema(description = "效期预警条数")
    private Integer expireWarningCount;

    /** 库存预警条数。 */
    @Schema(description = "库存预警条数")
    private Integer lowStockWarningCount;

    /** 效期预警天数阈值。 */
    @Schema(description = "效期预警天数阈值")
    private Integer expireWarningDays;

    /** 库存预警包装单位阈值。 */
    @Schema(description = "库存预警包装单位阈值")
    private Integer minPackageQuantityWarning;
}
