/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.assembler;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;

/**
 * 3505 {@code rtal_docno}（零售单据号）= 省平台费用明细流水号 {@code feedetl_sn}。
 *
 * <p>与门诊 2204 / 住院 2301 组装口径一致：优先收费项 {@code busNo}，否则收费项主键字符串。
 * 须与同次结算上报的 {@code feedetl_sn} 完全一致，禁止用发放单 busNo 顶替。
 * 3506 无此字段；吉林退货对单用 {@code medins_prod_sel_no}=原销售 {@code fixmedins_bchno}，不引用本值。</p>
 */
public final class InventoryUploadRtalDocnoAssembler {

    private InventoryUploadRtalDocnoAssembler() {
    }

    /**
     * @param chargeItem 与发放行关联的收费项
     * @return 零售单据号；无收费项时 null
     */
    public static String resolveFromChargeItem(ChargeItem chargeItem) {
        // 无收费项则无法对齐省平台 feedetl_sn
        if (chargeItem == null || chargeItem.getId() == null) {
            return null;
        }
        // 优先收费项业务单号（与 2204/2301 同源）
        if (isNotEmpty(chargeItem.getBusNo())) {
            return chargeItem.getBusNo().trim();
        }
        // 无 busNo 时用收费项主键字符串兜底（仍禁止用发放单号）
        return String.valueOf(chargeItem.getId());
    }
}
