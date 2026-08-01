/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.inventoryopening.response;

import com.openhis.health.application.basic.inventoryopening.dto.InventoryOpeningListRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 期初单分页列表响应（IF-BAS-INV-OPN-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "期初单分页响应")
public class PageInventoryOpeningsResponse {

    /** 去重后总单数。 */
    @Schema(description = "总条数")
    private Long total;

    /** 当前页聚合行。 */
    @Schema(description = "列表行")
    private List<InventoryOpeningListRowDto> records;
}
