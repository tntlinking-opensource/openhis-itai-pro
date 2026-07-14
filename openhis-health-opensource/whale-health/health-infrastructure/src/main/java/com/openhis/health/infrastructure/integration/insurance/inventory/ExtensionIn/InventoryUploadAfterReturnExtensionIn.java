/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 退药成功后触发第三方进销存上报的命令载荷（与发药上报成对）。
 */
@Data
@Accessors(chain = true)
public class InventoryUploadAfterReturnExtensionIn {

    /** 就诊主键，关联医保就诊上下文。 */
    private Long encounterId;

    /** 已退药成功的发放行主键列表。 */
    private List<Long> dispenseIds = new ArrayList<>();

    /** 医保/第三方合同业务号。 */
    private String contractBusNo;

    /** 合同主键。 */
    private Long contractId;
}
