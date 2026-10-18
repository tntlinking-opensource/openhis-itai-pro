/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 采购退货审核通过后触发 3504 退货上报（成功后伴生 3502/109，批次=退供单号） */
@Data
@Accessors(chain = true)
public class UploadAfterPurchaseReturnCommand {

    private String returnBusNo;
    private List<Long> supplyRequestLineIds = new ArrayList<>();
    private String contractBusNo;
    private Long contractId;
}
