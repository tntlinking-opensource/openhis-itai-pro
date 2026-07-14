/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.assembler;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;


/**
 * 进销存批次流水号 {@code fixmedins_bchno} 统一规则静态辅助（无 IO、无 Spring 依赖）。
 *
 * <p>优先使用批号 lotNumber；无批号时回落机构目录编码 itemBusNo（见进销存规范 §4.2）。</p>
 */
public final class InventoryUploadBatchNoAssembler {

    private InventoryUploadBatchNoAssembler() {
    }

    /**
     * 优先批号；无批号时回落机构目录编码 */
    public static String buildFixmedinsBchno(String itemBusNo, String lotNumber) {
        if (isNotEmpty(lotNumber)) {
            return lotNumber.trim();
        }
        return trimToNull(itemBusNo);
    }
}
