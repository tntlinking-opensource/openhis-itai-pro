/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.config.diagnosis.request;

import lombok.Data;

/**
 * 保存常见结果请求（CFG-09）。
 */
@Data
public class SaveCommonResultRequest {

    /** 常见结果主键；更新时必填。 */
    private Long id;

    /** 绑定小项观察定义主键。 */
    private Long observationDefinitionId;

    /** 科室机构主键。 */
    private Long deptOrgId;

    /** 模板名称。 */
    private String display;

    /** 默认结果值。 */
    private String valueString;

    /** 解读码。 */
    private String interpretationCode;

    /** 排序号。 */
    private Integer sortNum;

    /** 状态：active / inactive。 */
    private String statusEnum;
}
