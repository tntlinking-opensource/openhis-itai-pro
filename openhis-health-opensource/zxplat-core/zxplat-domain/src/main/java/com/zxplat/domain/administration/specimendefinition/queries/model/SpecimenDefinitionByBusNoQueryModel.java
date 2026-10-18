/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.specimendefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 按业务编码查询样本定义投影。
 */
@Getter
@Setter
public class SpecimenDefinitionByBusNoQueryModel {
    /** 主键。 */
    private Long id;
    /** 业务编码。 */
    private String busNo;
    /** 样本名称。 */
    private String name;
    /** 标本种类。 */
    private String typeEnum;
    /** 发布状态。 */
    private String statusEnum;
}
