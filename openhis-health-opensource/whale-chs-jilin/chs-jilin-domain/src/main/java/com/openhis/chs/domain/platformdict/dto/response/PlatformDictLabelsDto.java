/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.platformdict.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

/** 字典 label 批量查询结果。 */
@Data
@Accessors(chain = true)
public class PlatformDictLabelsDto {

    private String dictType;
    private Map<String, String> labelsByValue;
}
