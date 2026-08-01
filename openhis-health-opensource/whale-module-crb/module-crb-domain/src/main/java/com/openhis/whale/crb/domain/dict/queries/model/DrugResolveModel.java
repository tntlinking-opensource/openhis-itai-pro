/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict.queries.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Mapper 用药品国家码解析结果。
 */
@Getter
@Setter
@Accessors(chain = true)
public class DrugResolveModel {

    /** 国家药品码。 */
    private String nationalDrugCode;

    /** 国家药品名称。 */
    private String nationalDrugName;

    /** 是否找到有效对照。 */
    private Boolean found;
}
