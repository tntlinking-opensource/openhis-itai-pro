/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitemdefinition.query;

import lombok.Getter;
import lombok.Setter;

import java.util.Collection;

/**
 * 收费项目定义查询命令：按实例维度、批次或实例集合拉取价表候选。
 */
@Getter
@Setter
public class ChargeItemDefinitionQueryCommand {
    /** 实例表标识。 */
    private String instanceTable;
    /** 单个业务实例主键。 */
    private Long instanceId;
    /** 批次号过滤（可空表示不限批次）。 */
    private String lotNo;
    /** 批量实例主键（与 instanceTable 联用）。 */
    private Collection<Long> instanceIds;
    /**
     * 可选价类 code 集合（如目录选品仅 {@code SALE}+{@code LOT_NUMBER_PRICE}）；
     * 空或 null 表示不按价类收窄。
     */
    private Collection<String> priceTypeCodes;
}
