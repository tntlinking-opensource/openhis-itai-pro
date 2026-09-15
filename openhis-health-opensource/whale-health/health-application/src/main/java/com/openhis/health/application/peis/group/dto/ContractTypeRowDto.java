/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 合同类型字典行（号源分池只读）。
 */
@Getter
@Setter
public class ContractTypeRowDto {

    /** 类型编码。 */
    private String typeCode;

    /** 显示名称。 */
    private String typeName;

    /** 排序号。 */
    private Integer sortOrder;
}
