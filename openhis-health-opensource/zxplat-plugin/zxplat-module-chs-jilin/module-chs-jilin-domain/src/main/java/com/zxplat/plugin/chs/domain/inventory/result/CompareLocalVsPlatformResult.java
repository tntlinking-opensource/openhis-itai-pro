/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 本地流水与平台查询差异比对汇总 */
@Data
@Accessors(chain = true)
public class CompareLocalVsPlatformResult {

    private String compareQueryInfno;
    private String localUploadInfno;
    private int platformRowCount;
    private int localRowCount;
    private int matchCount;
    private int localOnlyCount;
    private int platformOnlyCount;
    private List<InvCompareRowResult> rows = new ArrayList<>();
}
