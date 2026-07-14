/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.platformdict.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 批量字典 value→label 查询 */
@Data
@Accessors(chain = true)
public class LookupPlatformDictLabelsCommand {

    /** 1901 type；默med_type（基线规范字典表） */
    private String dictType;

    private List<String> dictValues;
}
