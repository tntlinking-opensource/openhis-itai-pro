/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict.queries.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 检验项目对照分页行模型。
 */
@Getter
@Setter
@Accessors(chain = true)
public class LabItemMapRowModel {

    /** 主键。 */
    private Long id;

    /** 院内项目业务码。 */
    private String itemBusNo;

    /** 院内项目名称。 */
    private String itemName;

    /** 国家/标化项目码。 */
    private String nationalItemCode;

    /** 结果形态 code。 */
    private String resultShape;

    /** 附件二范围 code。 */
    private String annex2Scope;

    /** 是否启用推送。 */
    private Boolean pushEnabled;

    /** 是否已对照（国家码非空）。 */
    private Boolean mapped;
}
