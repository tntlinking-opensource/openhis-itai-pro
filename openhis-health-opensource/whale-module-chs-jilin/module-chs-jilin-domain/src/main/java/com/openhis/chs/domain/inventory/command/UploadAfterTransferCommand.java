/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 调拨审核通过后触发 3502 库存变更上报 */
@Data
@Accessors(chain = true)
public class UploadAfterTransferCommand {

    private String transferBusNo;
    private String supplyTypeCode;
    private String invChgType;
    private List<Long> supplyRequestLineIds = new ArrayList<>();
    private String contractBusNo;
    private Long contractId;
}
