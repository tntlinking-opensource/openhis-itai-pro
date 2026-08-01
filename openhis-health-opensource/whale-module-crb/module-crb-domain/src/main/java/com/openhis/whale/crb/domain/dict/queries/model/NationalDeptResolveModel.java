/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict.queries.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Mapper 用科室国家码解析结果。
 */
@Getter
@Setter
@Accessors(chain = true)
public class NationalDeptResolveModel {

    /** 国家科室码。 */
    private String targetDeptCode;

    /** 国家科室名称。 */
    private String targetDeptName;

    /** 是否找到有效对照。 */
    private Boolean found;
}
