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
 * 报损报溢审批通过后触发第三方进销存上报的命令载荷。
 */
@Data
@Accessors(chain = true)
public class InventoryUploadAfterGainLossExtensionIn {

    /** 报损报溢单业务号。 */
    private String gainLossBusNo;

    /** 供应类型 code，区分药库/药房/科室库存场景。 */
    private String supplyTypeCode;

    /** 已审批通过的供应行主键列表。 */
    private List<Long> supplyRequestLineIds = new ArrayList<>();

    /** 医保/第三方合同业务号。 */
    private String contractBusNo;

    /** 合同主键。 */
    private Long contractId;
}
