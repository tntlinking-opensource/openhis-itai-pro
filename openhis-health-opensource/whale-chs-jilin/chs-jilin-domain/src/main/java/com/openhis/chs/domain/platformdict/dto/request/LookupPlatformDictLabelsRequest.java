/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.platformdict.dto.request;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 批量字典 value→label 查询。 */
@Data
@Accessors(chain = true)
public class LookupPlatformDictLabelsRequest {

    /** 1901 type；默认 med_type（基线规范字典表）。 */
    private String dictType;

    private List<String> dictValues;
}
