/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.encounterMetadata.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class MetadataValuesDto {

    /** 解析结果 fieldKey → 展示值 */
    private Map<String, String> values = new LinkedHashMap<>();
    /** 已做脱敏展示的 fieldKey */
    private List<String> maskedFields = new ArrayList<>();
}
