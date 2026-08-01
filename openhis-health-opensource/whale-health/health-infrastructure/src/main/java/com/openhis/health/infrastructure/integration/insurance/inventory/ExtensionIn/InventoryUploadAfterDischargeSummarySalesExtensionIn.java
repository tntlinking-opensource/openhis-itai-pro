/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院清床后触发汇总径净销售 3505 上报的命令载荷。
 *
 * <p>由 {@link com.openhis.health.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension#uploadAfterDischargeSummarySales}
 * 消费；扩展侧按就诊汇总 {@code summary_no} 非空发放行（发退相抵、按天+单价分桶）后异步上传。</p>
 */
@Data
@Accessors(chain = true)
public class InventoryUploadAfterDischargeSummarySalesExtensionIn {

    /** 就诊主键 {@code adm_encounter.id}（入院至清床区间内汇总发药净量归属）。 */
    private Long encounterId;

    /** 医保/第三方合同业务号；无合同时可为空，由 adapter 从支付单回落。 */
    private String contractBusNo;

    /** 合同主键，与 {@code contractBusNo} 二选一或同时携带。 */
    private Long contractId;
}
