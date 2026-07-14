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
 * 调拨入/出库审批通过后触发第三方进销存上报的命令载荷。
 */
@Data
@Accessors(chain = true)
public class InventoryUploadAfterTransferExtensionIn {

    /** 调拨单业务号。 */
    private String transferBusNo;

    /** 供应类型 code。 */
    private String supplyTypeCode;

    /** 库存变动类型，区分调出/调入侧上报。 */
    private String invChgType;

    /** 已审批通过的供应行主键列表。 */
    private List<Long> supplyRequestLineIds = new ArrayList<>();

    /** 医保/第三方合同业务号。 */
    private String contractBusNo;

    /** 合同主键。 */
    private Long contractId;
}
