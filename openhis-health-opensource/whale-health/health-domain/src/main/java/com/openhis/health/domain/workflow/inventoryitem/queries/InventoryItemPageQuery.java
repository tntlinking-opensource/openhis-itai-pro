/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.inventoryitem.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 库存项分页查询入参。
 */
@Getter
@Setter
public class InventoryItemPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 项来源表。 */
    private String itemTable;

    /** 项主键。 */
    private Long itemId;

    /** 位置主键。 */
    private Long locationId;

    /** 库存状态枚举编码。 */
    private String inventoryStatusEnum;

    /** true 时仅返回账面数量大于零的行（与库存明细「库存大于 0」口径一致）。 */
    private Boolean positiveQuantityOnly;
}
