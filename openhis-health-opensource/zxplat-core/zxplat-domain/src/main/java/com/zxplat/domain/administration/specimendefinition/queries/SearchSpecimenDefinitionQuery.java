/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.specimendefinition.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 样本定义关键字检索入参。
 */
@Getter
@Setter
public class SearchSpecimenDefinitionQuery {
    /** 关键字。 */
    private String keyword;
    /** 发布状态；默认仅 ACTIVE。 */
    private String statusEnum;
    /** 标本种类过滤。 */
    private String typeEnum;
    /** 返回条数上限，默认 50。 */
    private Integer limit;
}
