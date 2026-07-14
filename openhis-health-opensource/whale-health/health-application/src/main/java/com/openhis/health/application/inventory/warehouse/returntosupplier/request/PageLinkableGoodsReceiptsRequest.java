/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returntosupplier.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 可关联进货单分页查询条件（IF-WHS-RT-12）。
 *
 * <p>分页字段继承 {@link AbstractHealthPageRequest}；须指定 {@code sourceLocationId} 作为退货源药库。</p>
 */
@Getter
@Setter
@Schema(description = "可关联进货单分页 IF-WHS-RT-12")
public class PageLinkableGoodsReceiptsRequest extends AbstractHealthPageRequest {

    /** 源药库 sourceLocationId。 */
    @Schema(description = "源药库 sourceLocationId")
    private Long sourceLocationId;
}
