/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 发药完成后触发 3502(107)→3505 销售上报 */
@Data
@Accessors(chain = true)
public class UploadAfterDispenseCommand {

    private Long encounterId;
    private List<Long> dispenseIds = new ArrayList<>();
    private String contractBusNo;
    private Long contractId;
}
