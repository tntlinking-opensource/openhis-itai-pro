/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.observationdefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 临床观察定义读模型（getById / search / listByCategory）。
 */
@Getter
@Setter
public class ObservationDefinitionModel {

    /** 主键。 */
    private Long id;

    /** 院内编码。 */
    private String code;

    /** 展示名称。 */
    private String display;

    /** 值类型。 */
    private String valueTypeEnum;

    /** 单位编码。 */
    private String unitCode;

    /** 分类编码。 */
    private String categoryCode;

    /** 状态。 */
    private String statusEnum;

    /** 执行科室机构主键（{@code adm_organization.id}）。 */
    private Long orgId;
}
