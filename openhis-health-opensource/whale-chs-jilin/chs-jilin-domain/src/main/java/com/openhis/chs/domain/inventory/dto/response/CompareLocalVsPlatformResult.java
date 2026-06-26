/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 本地流水与平台查询差异比对汇总。 */
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
    private List<InvCompareRowDto> rows = new ArrayList<>();
}
