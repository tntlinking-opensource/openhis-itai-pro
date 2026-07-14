/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.platformdict.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/** 字典 label 批量查询结果 */
@Data
@Accessors(chain = true)
public class PlatformDictLabelsResult {

    private String dictType;
    private Map<String, String> labelsByValue;
}
