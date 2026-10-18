/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.inventoryopening.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 期初选品分页请求（IF-BAS-INV-OPN-11）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "期初选品分页")
public class PageProductsForInventoryOpeningRequest extends AbstractHealthPageRequest {

    /** 目标库房（上下文；门禁用）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库房主键")
    private Long locationId;

    /** 物品表名白名单（药品/耗材）；空表示并集检索。 */
    @Schema(description = "物品表名")
    private String productTableName;
}
