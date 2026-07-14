/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.specimendefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 样本定义分页行投影。
 */
@Getter
@Setter
public class SpecimenDefinitionPageItemQueryModel {
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
    /** 拼音码。 */
    private String pyStr;
    /** 五笔码。 */
    private String wbStr;
    /** 最近更新时间。 */
    private LocalDateTime updatedAt;
}
