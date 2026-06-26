/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class ClearOrg3206AResult {

    private Integer pages;
    private Integer pageNum;
    private Integer pageSize;
    /** 平台返回的清算机构明细（原样 JSON 对象列表）。 */
    private List<Map<String, Object>> clrOptins;
}
