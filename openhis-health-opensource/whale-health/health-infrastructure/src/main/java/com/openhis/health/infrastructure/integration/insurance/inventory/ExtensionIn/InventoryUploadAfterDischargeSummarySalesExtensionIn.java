/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院清床（或无床直结）后触发净销售合并 3505 上报的命令载荷。
 *
 * <p>由 {@link com.openhis.health.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension#uploadAfterDischargeSummarySales}
 * 消费；扩展侧按就诊全部已核发/已退药发放行发退相抵，按药品+批号+包装锚单价分桶合并后异步上传
 * （读 {@code MedicationDispense.trace_no}；追溯码去重；{@code trdn_flag} 按码累计实扣）。</p>
 */
@Data
@Accessors(chain = true)
public class InventoryUploadAfterDischargeSummarySalesExtensionIn {

    /** 就诊主键 {@code adm_encounter.id}（入院至清床/无床直结区间内发药净量归属）。 */
    private Long encounterId;

    /** 医保/第三方合同业务号；无合同时可为空，由 adapter 从支付单回落。 */
    private String contractBusNo;

    /** 合同主键，与 {@code contractBusNo} 二选一或同时携带。 */
    private Long contractId;
}
