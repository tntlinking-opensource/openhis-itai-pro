/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 损益审核通过后触发 3502 库存变更上报 */
@Data
@Accessors(chain = true)
public class UploadAfterGainLossCommand {

    private String gainLossBusNo;
    private String supplyTypeCode;
    private List<Long> supplyRequestLineIds = new ArrayList<>();
    private String contractBusNo;
    private Long contractId;
}
