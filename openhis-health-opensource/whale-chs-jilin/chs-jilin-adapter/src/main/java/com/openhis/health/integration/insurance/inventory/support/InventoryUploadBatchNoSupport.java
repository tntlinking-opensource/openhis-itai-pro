/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import org.apache.commons.lang3.StringUtils;

/** 进销存批次流水号 {@code fixmedins_bchno} 统一规则（§4.2）。 */
public final class InventoryUploadBatchNoSupport {

    private InventoryUploadBatchNoSupport() {
    }

    /**
     * 优先批号；无批号时回落机构目录编码。
     */
    public static String buildFixmedinsBchno(String itemBusNo, String lotNumber) {
        if (StringUtils.isNotBlank(lotNumber)) {
            return lotNumber.trim();
        }
        return trimToNull(itemBusNo);
    }
}