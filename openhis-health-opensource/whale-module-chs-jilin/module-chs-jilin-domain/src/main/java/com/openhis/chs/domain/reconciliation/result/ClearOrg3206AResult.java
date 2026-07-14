/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/** 【3206A】清算机构查询出参 */
@Data
@Accessors(chain = true)
public class ClearOrg3206AResult {

    private Integer pages;
    private Integer pageNum;
    private Integer pageSize;
    /** 平台返回的清算机构明细（原样 JSON 对象列表） */
    private List<Map<String, Object>> clrOptins;
}
