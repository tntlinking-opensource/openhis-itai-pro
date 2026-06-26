/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

/** 单条本地 vs 平台比对结果。 */
@Data
@Accessors(chain = true)
public class InvCompareRowDto {

    /** {@link com.openhis.chs.domain.inventory.ChsInventoryConstants} COMPARE_DIFF_* */
    private String diffType;
    private String compareKey;
    private String fixmedinsBchno;
    private String medListCodg;
    /** 3512/3513 追溯比对键。 */
    private String drugTracCodg;
    private Long localUploadId;
    private String localUploadStatus;
    private String localCreatedAt;
    private Map<String, Object> platformRow = new HashMap<>();
}
