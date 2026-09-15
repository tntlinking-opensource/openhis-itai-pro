/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.group.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 团检合同类型字典（peis_contract_type）。
 */
@Getter
@Setter
@Entity
@Table(name = "peis_contract_type")
public class PeisContractType extends BaseAuditedEntity {

    /** 类型编码。 */
    @Column(name = "type_code", nullable = false, length = 32)
    private String typeCode;

    /** 显示名称。 */
    @Column(name = "type_name", nullable = false, length = 64)
    private String typeName;

    /** 排序号。 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** 是否启用。 */
    @Column(name = "enabled_flag", nullable = false)
    private Boolean enabledFlag = Boolean.TRUE;
}
