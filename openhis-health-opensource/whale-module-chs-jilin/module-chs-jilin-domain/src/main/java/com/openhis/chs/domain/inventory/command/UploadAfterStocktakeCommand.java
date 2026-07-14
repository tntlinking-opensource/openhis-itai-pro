/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 盘点审核通过后触发 3502 盘盈/盘损上报（3501 仅首次初始化） */
@Data
@Accessors(chain = true)
public class UploadAfterStocktakeCommand {

    private String stocktakeBusNo;
    private String supplyTypeCode;
    private List<Long> supplyRequestLineIds = new ArrayList<>();
    private String contractBusNo;
    private Long contractId;
    private String sourceType;
}
