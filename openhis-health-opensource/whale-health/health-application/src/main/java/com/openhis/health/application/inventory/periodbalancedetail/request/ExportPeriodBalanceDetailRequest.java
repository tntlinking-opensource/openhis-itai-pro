/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.periodbalancedetail.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 库房期初期末明细导出入参（IF-INV-SET-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库房期初期末明细导出 IF-INV-SET-03", requiredProperties = {"locationId", "startBackupBusNo"})
public class ExportPeriodBalanceDetailRequest extends AbstractPageQuery {

    /** 选中库房主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "选中库房主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long locationId;

    /** 期初备份单号。 */
    @Schema(description = "期初备份单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String startBackupBusNo;

    /** 期末备份单号；空表示实时库存。 */
    @Schema(description = "期末备份单号；空表示实时库存")
    private String endBackupBusNo;

    /** 物品主表；不传时由服务端按库位现库/期初快照 DISTINCT item_table 解析。 */
    @Schema(description = "物品主表；不传时由服务端按库位现库/期初快照解析")
    private String itemTable;

    /** 默认 true：隐藏期间全 0 业务行。 */
    @Schema(description = "默认 true：隐藏期间全 0 业务行")
    private Boolean isBus;

    /** 账平筛选：true 仅账平，false 仅不平，null 不过滤。 */
    @Schema(description = "账平筛选：true 仅账平，false 仅不平，null 不过滤")
    private Boolean isBalance;
}
