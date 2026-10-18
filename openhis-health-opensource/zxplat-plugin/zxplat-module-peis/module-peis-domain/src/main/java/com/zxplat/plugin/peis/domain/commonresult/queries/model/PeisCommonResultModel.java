/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.commonresult.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * PEIS 常见结果读模型。
 */
@Getter
@Setter
public class PeisCommonResultModel {

    private Long id;
    private String display;
    private String valueString;
    private String interpretationCode;
    private Integer sortNum;
    private String statusEnum;
    private Long deptOrgId;
    private Long observationDefinitionId;
}
