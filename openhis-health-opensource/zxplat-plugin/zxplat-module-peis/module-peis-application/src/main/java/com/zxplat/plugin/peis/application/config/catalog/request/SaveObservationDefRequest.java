/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.config.catalog.request;

import lombok.Data;

/**
 * 保存体检小项（ObservationDefinition）请求（CFG-03）。
 */
@Data
public class SaveObservationDefRequest {

    /** 小项主键；更新时必填。 */
    private Long definitionId;

    /** 小项编码。 */
    private String code;

    /** 小项名称。 */
    private String display;

    /** 值类型：quantity / string / codeable / boolean。 */
    private String valueTypeEnum;

    /** 分类编码：exam_item / dept_conclusion 等。 */
    private String categoryCode;

    /** 单位编码。 */
    private String unitCode;

    /** 状态：active / inactive。 */
    private String statusEnum;

    /** 执行科室机构主键。 */
    private Long orgId;
}
