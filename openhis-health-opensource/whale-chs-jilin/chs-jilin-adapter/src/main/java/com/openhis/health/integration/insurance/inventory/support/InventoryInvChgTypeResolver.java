/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.inventory.ChsInventoryConstants;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.Set;

/** 3502 inv_chg_type 解析（盘点/损益行 reason 或盈亏符号回落）。 */
public final class InventoryInvChgTypeResolver {

    private static final Set<String> POSITIVE_TYPES = Set.of(
        ChsInventoryConstants.INV_CHG_TYPE_TRANSFER_IN,
        ChsInventoryConstants.INV_CHG_TYPE_STOCKTAKE_PROFIT,
        ChsInventoryConstants.INV_CHG_TYPE_OTHER_IN,
        ChsInventoryConstants.INV_CHG_TYPE_PURCHASE_IN,
        ChsInventoryConstants.INV_CHG_TYPE_GIFT_IN
    );

    private static final Set<String> NEGATIVE_TYPES = Set.of(
        ChsInventoryConstants.INV_CHG_TYPE_TRANSFER_OUT,
        ChsInventoryConstants.INV_CHG_TYPE_STOCKTAKE_LOSS,
        ChsInventoryConstants.INV_CHG_TYPE_DESTROY,
        ChsInventoryConstants.INV_CHG_TYPE_OTHER_OUT,
        ChsInventoryConstants.INV_CHG_TYPE_PURCHASE_RETURN_OUT,
        ChsInventoryConstants.INV_CHG_TYPE_GIFT_RETURN_OUT
    );

    private static final Set<String> ALL_TYPES = Set.of(
        ChsInventoryConstants.INV_CHG_TYPE_TRANSFER_IN,
        ChsInventoryConstants.INV_CHG_TYPE_TRANSFER_OUT,
        ChsInventoryConstants.INV_CHG_TYPE_STOCKTAKE_PROFIT,
        ChsInventoryConstants.INV_CHG_TYPE_STOCKTAKE_LOSS,
        ChsInventoryConstants.INV_CHG_TYPE_DESTROY,
        ChsInventoryConstants.INV_CHG_TYPE_OTHER_IN,
        ChsInventoryConstants.INV_CHG_TYPE_OTHER_OUT,
        ChsInventoryConstants.INV_CHG_TYPE_PURCHASE_IN,
        ChsInventoryConstants.INV_CHG_TYPE_PURCHASE_RETURN_OUT,
        ChsInventoryConstants.INV_CHG_TYPE_GIFT_IN,
        ChsInventoryConstants.INV_CHG_TYPE_GIFT_RETURN_OUT
    );

    private InventoryInvChgTypeResolver() {
    }

    public static String resolveInvChgType(SupplyRequestModel line) {
        if (line == null || line.getItemQuantity() == null
            || line.getItemQuantity().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal qty = line.getItemQuantity();
        String reason = trimToNull(line.getReason());
        if (reason != null && ALL_TYPES.contains(reason)) {
            validateDirection(reason, qty);
            return reason;
        }
        return qty.compareTo(BigDecimal.ZERO) > 0
            ? ChsInventoryConstants.INV_CHG_TYPE_STOCKTAKE_PROFIT
            : ChsInventoryConstants.INV_CHG_TYPE_STOCKTAKE_LOSS;
    }

    private static void validateDirection(String invChgType, BigDecimal qty) {
        if (POSITIVE_TYPES.contains(invChgType) && qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("inv_chg_type=" + invChgType + " 要求盈亏数量为正");
        }
        if (NEGATIVE_TYPES.contains(invChgType) && qty.compareTo(BigDecimal.ZERO) >= 0) {
            throw new IllegalStateException("inv_chg_type=" + invChgType + " 要求盈亏数量为负");
        }
    }
}