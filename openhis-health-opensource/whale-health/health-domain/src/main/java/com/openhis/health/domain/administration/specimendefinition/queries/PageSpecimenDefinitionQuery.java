/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.specimendefinition.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 样本定义分页查询入参。
 */
@Getter
@Setter
public class PageSpecimenDefinitionQuery {
    /** 关键字（名称/编码/拼音/五笔）。 */
    private String keyword;
    /** 发布状态过滤。 */
    private String statusEnum;
    /** 标本种类过滤。 */
    private String typeEnum;
}
