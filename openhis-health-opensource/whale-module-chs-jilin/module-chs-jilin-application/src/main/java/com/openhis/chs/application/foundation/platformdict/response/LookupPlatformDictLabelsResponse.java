/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.foundation.platformdict.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

/** 平台字典标签批量查询出参 */
@Data
@Accessors(chain = true)
public class LookupPlatformDictLabelsResponse {

    /** 编码 → 显示标签映射（保持插入顺序） */
    private Map<String, String> labels = new LinkedHashMap<>();
}
