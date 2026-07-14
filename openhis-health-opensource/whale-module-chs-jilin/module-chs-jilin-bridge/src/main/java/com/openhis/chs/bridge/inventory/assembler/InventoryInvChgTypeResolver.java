/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.assembler;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.share.enums.InvChgType;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;

import java.math.BigDecimal;

/**
 * 3502 库存变更类型 {@code inv_chg_type} 解析静态辅助（无 IO、无 Spring 依赖）。
 *
 * <p>优先取 supply_request 行 reason 字段（须为表 311 数字编码 101～111）；
 * 否则按盈亏数量符号回落盘盈（103）或盘损（104），并校验方向与数量符号一致性。</p>
 */
public final class InventoryInvChgTypeResolver {

    private InventoryInvChgTypeResolver() {
    }

    /**
     * 解析单行 inv_chg_type 数字编码。
     *
     * @param line 盘点/损益 supply_request 行
     * @return 101～111 编码，无法解析时 {@code null}
     */
    public static String resolveInvChgType(SupplyRequestModel line) {
        if (line == null || line.getItemQuantity() == null
            || line.getItemQuantity().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal qty = line.getItemQuantity();
        String reason = trimToNull(line.getReason());
        InvChgType typed = InvChgType.fromCodeOptional(reason);
        if (typed != null) {
            typed.validateQtyDirection(qty);
            return typed.getCode();
        }
        if (qty.compareTo(BigDecimal.ZERO) > 0) {
            return InvChgType.STOCKTAKE_PROFIT.getCode();
        }
        return InvChgType.STOCKTAKE_LOSS.getCode();
    }
}
