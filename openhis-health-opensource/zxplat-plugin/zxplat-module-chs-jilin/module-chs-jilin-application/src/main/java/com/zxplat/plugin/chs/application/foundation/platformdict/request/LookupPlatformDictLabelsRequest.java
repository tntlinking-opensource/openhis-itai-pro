/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.foundation.platformdict.request;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 平台字典标签批量查询入参 */
@Data
@Accessors(chain = true)
public class LookupPlatformDictLabelsRequest {

    /** 字典类型（如 ADMDVS、med_type） */
    private String dictType;
    /** 待查询的字典编码列表 */
    private List<String> dictValues = new ArrayList<>();
}
