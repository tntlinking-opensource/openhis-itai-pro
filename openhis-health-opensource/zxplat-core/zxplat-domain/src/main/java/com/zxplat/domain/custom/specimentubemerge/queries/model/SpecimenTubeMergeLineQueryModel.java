/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.specimentubemerge.queries.model;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * 合管配置行读模型（展示 enrich 由应用层完成）。
 */
@Getter
@Setter
public class SpecimenTubeMergeLineQueryModel {

    /** 绑定行主键。 */
    private Long id;

    /** 设备主键。 */
    private Long deviceId;

    /** 设备内合管组序号（从 1 起）。 */
    private Integer groupNo;

    /** 目标表名。 */
    private String targetTable;

    /** 目标主键。 */
    private Long targetId;

    /** 耗材数量。 */
    private BigDecimal quantity;

    /** 耗材单位 code。 */
    private String unitCode;

    /** 展示排序。 */
    private Integer displayOrder;
}
