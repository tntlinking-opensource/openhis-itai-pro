/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitemdefinition.query;

import lombok.Value;
import com.openhis.whale.core.application.util.StringUtils;

/**
 * 批号维定价批量查询键：实例表 + 实例主键 + 批号（与 {@code adm_charge_item_definition} 联合锚点一致）。
 */
@Value
public class ChargeItemDefinitionLotBatchKey {

    /** 上游业务表名（须非空白）。 */
    String instanceTable;

    /** 业务实例主键。 */
    Long instanceId;

    /** 批号（已 trim）。 */
    String lotNo;

    /**
     * 从库存行三列构造键；任一项非法时返回 {@code null}，调用方跳过批量集。
     *
     * @param itemTable 物品表名
     * @param itemId    物品定义主键
     * @param lotNumber 批号
     * @return 键或 null
     */
    public static ChargeItemDefinitionLotBatchKey maybeOf(String itemTable, Long itemId, String lotNumber) {
        if (StringUtils.isEmpty(itemTable)) {
            return null;
        }
        if (itemId == null) {
            return null;
        }
        if (StringUtils.isEmpty(lotNumber)) {
            return null;
        }
        return new ChargeItemDefinitionLotBatchKey(itemTable.trim(), itemId, lotNumber.trim());
    }

    /**
     * 用于 {@link java.util.Map} 的复合键：分隔符取 SOH，避免与业务表名/批号字面量碰撞（通用规范复合键门禁）。
     *
     * @return 稳定字符串键
     */
    public String compositeKey() {
        return this.instanceTable + '\u0001' + this.instanceId + '\u0001' + this.lotNo;
    }
}
