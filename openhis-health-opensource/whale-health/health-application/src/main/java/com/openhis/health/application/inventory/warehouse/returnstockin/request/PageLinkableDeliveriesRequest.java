/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returnstockin.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 可关联药库出库/配送单分页查询条件（IF-WHS-RSI-12，无主动建单入口：接口固定拒绝 WHS_RSI_006）。
 */
@Getter
@Setter
@Schema(description = "可关联出库/配送单分页 IF-WHS-RSI-12（已拒绝）")
public class PageLinkableDeliveriesRequest extends AbstractHealthPageRequest {

    /** 源药库 sourceLocationId。 */
    @Schema(description = "源药库 sourceLocationId")
    private Long sourceLocationId;
}
