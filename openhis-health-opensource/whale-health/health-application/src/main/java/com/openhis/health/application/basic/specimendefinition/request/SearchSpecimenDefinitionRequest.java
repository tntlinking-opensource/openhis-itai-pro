/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.specimendefinition.request;

import lombok.Data;

/**
 * 样本定义关键字检索请求（下拉选品等场景）。
 */
@Data
public class SearchSpecimenDefinitionRequest {

    /** 关键字（名称/编码/拼音/五笔）。 */
    private String keyword;

    /** 发布状态；默认仅 ACTIVE。 */
    private String statusEnum;

    /** 标本种类过滤。 */
    private String typeEnum;

    /** 返回条数上限，默认 50。 */
    private Integer limit;
}
