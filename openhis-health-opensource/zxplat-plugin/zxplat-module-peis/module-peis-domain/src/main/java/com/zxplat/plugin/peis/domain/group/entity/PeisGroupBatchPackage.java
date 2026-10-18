/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.group.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 体检分组可选套餐（须 ⊆ 合同套餐池）。
 */
@Getter
@Setter
@Entity
@Table(name = "peis_group_batch_package")
public class PeisGroupBatchPackage extends BaseAuditedEntity {

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "package_activity_id", nullable = false)
    private Long packageActivityId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
