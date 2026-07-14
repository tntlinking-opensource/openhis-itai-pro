/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 首次批初始化单行出参 */
@Data
@Accessors(chain = true)
public class InitializeInventoryLineResult {

    private Long inventoryItemId;
    private String fixmedinsBchno;
    private String medListCodg;
    private String upload3501Status;
    private String upload3502Status;
    private Long upload3501Id;
    private Long upload3502Id;
    private String platformMsg;
}
