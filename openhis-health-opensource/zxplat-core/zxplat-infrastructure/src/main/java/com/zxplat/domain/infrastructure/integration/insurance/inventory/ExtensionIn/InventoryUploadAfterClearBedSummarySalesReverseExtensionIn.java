/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.inventory.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 反结/召回后冲销清床汇总径净销售 3506 的命令载荷。
 *
 * <p>由 {@link com.zxplat.domain.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension#uploadAfterClearBedSummarySalesReverse}
 * 消费；无本就诊成功清床汇总 3505 时为 no-op（覆盖未清床反结）。
 * {@code fixmedins_bchno} 须为退药单 busNo（可传 {@link #dispenseIds}）。</p>
 */
@Data
@Accessors(chain = true)
public class InventoryUploadAfterClearBedSummarySalesReverseExtensionIn {

    /** 就诊主键 {@code adm_encounter.id}。 */
    private Long encounterId;

    /** 医保/第三方合同业务号；可空。 */
    private String contractBusNo;

    /** 合同主键；可空。 */
    private Long contractId;

    /**
     * 已生成退药发放行主键（与清床 3505 冲销行按序配对）；可空。
     * <p>空时由 CHS bridge 按审计现场生成退药单。</p>
     */
    private List<Long> dispenseIds;
}
