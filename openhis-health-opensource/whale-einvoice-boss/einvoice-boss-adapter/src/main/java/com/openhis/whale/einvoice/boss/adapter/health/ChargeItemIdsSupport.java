package com.openhis.whale.einvoice.boss.adapter.health;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 解析 fin_payment_reconciliation.charge_item_ids 逗号分隔串。
 */
public final class ChargeItemIdsSupport {

    private ChargeItemIdsSupport() {
    }

    /**
     * 将 charge_item_ids 解析为 Long 列表。
     *
     * @param chargeItemIds 逗号分隔 ID 串
     * @return 主键列表
     */
    public static List<Long> parse(String chargeItemIds) {
        if (StringUtils.isBlank(chargeItemIds)) {
            return Collections.emptyList();
        }
        String[] parts = chargeItemIds.split(",");
        List<Long> ids = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            ids.add(Long.parseLong(trimmed));
        }
        return ids;
    }
}
