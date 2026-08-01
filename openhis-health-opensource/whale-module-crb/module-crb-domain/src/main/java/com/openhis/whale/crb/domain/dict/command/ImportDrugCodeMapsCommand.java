/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict.command;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 药品对照批量导入命令。
 */
@Getter
@Setter
@Accessors(chain = true)
public class ImportDrugCodeMapsCommand {

    /** 租户 id。 */
    private Long tenantId;

    /** 导入行（每行同 UpsertDrugCodeMapCommand 字段）。 */
    private List<UpsertDrugCodeMapCommand> rows = new ArrayList<>();
}
