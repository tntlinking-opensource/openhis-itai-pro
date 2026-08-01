/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.inventoryopening.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 期初单分页列表请求（IF-BAS-INV-OPN-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "期初单分页列表")
public class PageInventoryOpeningsRequest extends AbstractHealthPageRequest {

    /** 目标库房主键（purpose_location_id）；空或 0（全部库房）表示不按库房筛。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库房主键；0=全部库房")
    private Long locationId;

    /** 供应状态码（SupplyStatus.getCode）。 */
    @Schema(description = "状态码")
    private String statusEnum;

    /** 业务单号模糊/精确（与关键字约定一致，应用层写入 listQuery）。 */
    @Schema(description = "单号")
    private String busNo;
}
