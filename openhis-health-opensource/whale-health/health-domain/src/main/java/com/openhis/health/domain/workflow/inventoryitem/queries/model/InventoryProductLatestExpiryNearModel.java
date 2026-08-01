/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.inventoryitem.queries.model;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 订货快捷效期读模型：品种在库批中<strong>最晚效期</strong>那一批的效期时刻（仅命中近效时返回）。
 *
 * <p>对应 {@code InventoryItemQueryService#listProductKeysWhoseLatestLotNearExpiry} /
 * AS-INV-WHS-001 BR-WHS-PO-014；与库存明细任意近效批号行口径不同。</p>
 */
@Getter
@Setter
public class InventoryProductLatestExpiryNearModel {

    /** 物品定义表名（如 {@code med_medication_definition}）。 */
    private String itemTable;

    /** 物品定义主键。 */
    private Long itemId;

    /** 该品种在库批（quantity&gt;0）中最晚的 {@code expiration_date}。 */
    private LocalDateTime latestExpirationDate;
}
