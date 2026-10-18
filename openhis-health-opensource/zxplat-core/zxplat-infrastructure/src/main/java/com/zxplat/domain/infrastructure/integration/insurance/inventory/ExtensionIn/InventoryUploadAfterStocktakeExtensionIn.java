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
 * 盘点审批通过后触发第三方进销存上报的命令载荷。
 */
@Data
@Accessors(chain = true)
public class InventoryUploadAfterStocktakeExtensionIn {

    /** 盘点单业务号。 */
    private String stocktakeBusNo;

    /** 供应类型 code。 */
    private String supplyTypeCode;

    /** 盘点差异已落库的供应行主键列表。 */
    private List<Long> supplyRequestLineIds = new ArrayList<>();

    /** 医保/第三方合同业务号。 */
    private String contractBusNo;

    /** 合同主键。 */
    private Long contractId;

    /** 盘点来源类型（如全盘/抽盘），adapter 按省份映射上报口径。 */
    private String sourceType;
}
