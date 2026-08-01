/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.assembler;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 进销存机构批次流水号 {@code fixmedins_bchno} 统一规则（无 IO、无 Spring 依赖）。
 *
 * <p>取值是<strong>对应业务操作单据的 busNo</strong>（防重码），不是生产批号、也不是品种目录编码：
 * 发药/退药取发放行 {@code MedicationDispense.busNo}（一行一号，视为一次独立发药/退药操作）；
 * 进货/盘点/损益/领退领/调拨取 supply 单据头 {@code busNo}（同单一码）；
 * <strong>退供 3504 / 退药 3506（吉林省）</strong>取<strong>原 3503/3505</strong> 的
 * {@code fixmedins_bchno}（见 {@link InventoryPurchaseUploadReferenceResolver} /
 * {@link InventorySaleUploadReferenceResolver}），不得用退供/退药单号；
 * <strong>退供伴生 3502</strong> 取<strong>退供单号</strong>（见 {@link InventoryPurchaseCompanion3502LineAssembler}）；
 * <strong>退药伴生 3502</strong> 取<strong>退药单号</strong>（{@code originalFixmedinsBchno}，见
 * {@link InventorySaleCompanion3502LineAssembler}）；
 * 住院清床合并 3505 取合并桶代表行 busNo。</p>
 */
public final class InventoryUploadBatchNoAssembler {

    private InventoryUploadBatchNoAssembler() {
    }

    /**
     * 将业务单据 busNo 规范为平台 {@code fixmedins_bchno}。
     *
     * @param businessBusNo 业务单据号；空则返回 null
     * @return 去空白后的业务 busNo，空输入为 null
     */
    public static String buildFixmedinsBchno(String businessBusNo) {
        return trimToNull(businessBusNo);
    }
}
