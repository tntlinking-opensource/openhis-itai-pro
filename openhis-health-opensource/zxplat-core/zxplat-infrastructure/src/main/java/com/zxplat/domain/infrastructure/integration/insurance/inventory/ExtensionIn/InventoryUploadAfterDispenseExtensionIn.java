/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.inventory.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 发药/核发成功后触发第三方进销存上报的命令载荷。
 *
 * <p>由 {@link com.zxplat.domain.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension#uploadAfterDispense} 消费；
 * 扩展未启用时 AppService 仍应组装但不上报。</p>
 */
@Data
@Accessors(chain = true)
public class InventoryUploadAfterDispenseExtensionIn {

    /** 就诊主键，门诊/住院核发场景用于关联医保就诊上下文。 */
    private Long encounterId;

    /** 已核发成功的发放行主键列表（{@code med_medication_dispense.id}）。 */
    private List<Long> dispenseIds = new ArrayList<>();

    /** 医保/第三方合同业务号；无合同时可为空。 */
    private String contractBusNo;

    /** 合同主键，与 {@code contractBusNo} 二选一或同时携带以适配不同 adapter。 */
    private Long contractId;
}
