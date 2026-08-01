/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict.queries.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 科室对照分页行模型。
 */
@Getter
@Setter
@Accessors(chain = true)
public class DeptCodeMapRowModel {

    /** 主键。 */
    private Long id;

    /** 院内科室业务码。 */
    private String locationBusNo;

    /** 院内科室名称。 */
    private String locationName;

    /** 国家科室码。 */
    private String targetDeptCode;

    /** 国家科室名称。 */
    private String targetDeptName;

    /** 是否已对照（国家码非空）。 */
    private Boolean mapped;
}
