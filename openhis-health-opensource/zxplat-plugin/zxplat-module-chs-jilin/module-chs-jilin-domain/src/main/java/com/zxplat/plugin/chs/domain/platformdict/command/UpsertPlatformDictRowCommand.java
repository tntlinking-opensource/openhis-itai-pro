/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.platformdict.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 1901 字典行 upsert 命令 */
@Data
@Accessors(chain = true)
public class UpsertPlatformDictRowCommand {

    private String dictType;
    private String dictValue;
    private String dictLabel;
    private String parentValue;
    private Integer sortOrder;
    private String valiFlag;
    private Long platformVersion;
}
