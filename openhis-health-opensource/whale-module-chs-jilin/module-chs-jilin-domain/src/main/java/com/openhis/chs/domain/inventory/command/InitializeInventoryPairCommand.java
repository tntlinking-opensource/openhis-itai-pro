/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 首次初始化：单行 3501 盘存 + 配对 3502 变更 */
@Data
@Accessors(chain = true)
public class InitializeInventoryPairCommand {

    private InvUploadLineCommand stocktake3501;
    private InvUploadLineCommand invChange3502;
}
