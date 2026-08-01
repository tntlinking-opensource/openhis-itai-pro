/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict.queries.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Mapper 用检验项目解析结果。
 */
@Getter
@Setter
@Accessors(chain = true)
public class LabItemResolveModel {

    /** 国家/标化项目码。 */
    private String nationalItemCode;

    /** 结果形态 code。 */
    private String resultShape;

    /** 附件二范围 code。 */
    private String annex2Scope;

    /** 是否启用推送。 */
    private Boolean pushEnabled;

    /** 是否找到有效对照。 */
    private Boolean found;
}
