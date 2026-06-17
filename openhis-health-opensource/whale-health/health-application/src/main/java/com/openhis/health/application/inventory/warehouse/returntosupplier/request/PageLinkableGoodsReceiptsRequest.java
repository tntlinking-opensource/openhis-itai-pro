/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returntosupplier.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class PageLinkableGoodsReceiptsRequest extends AbstractHealthPageRequest {

    /** 源药库位置主键。 */
    @Schema(description = "源药库 sourceLocationId")
    private Long sourceLocationId;
}
