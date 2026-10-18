/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

/** 单条本地 vs 平台比对结果 */
@Data
@Accessors(chain = true)
public class InvCompareRowResult {

    /** {@link com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants} COMPARE_DIFF_* */
    private String diffType;
    private String compareKey;
    private String fixmedinsBchno;
    private String medListCodg;
    /** 3512/3513 追溯比对键 */
    private String drugTracCodg;
    private Long localUploadId;
    private String localUploadStatus;
    private String localCreatedAt;
    private Map<String, Object> platformRow = new HashMap<>();
}
