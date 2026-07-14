/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 退药完成后触发 3506 销售退货上报 */
@Data
@Accessors(chain = true)
public class UploadAfterReturnCommand {

    private Long encounterId;
    private List<Long> dispenseIds = new ArrayList<>();
    private String contractBusNo;
    private Long contractId;
}
