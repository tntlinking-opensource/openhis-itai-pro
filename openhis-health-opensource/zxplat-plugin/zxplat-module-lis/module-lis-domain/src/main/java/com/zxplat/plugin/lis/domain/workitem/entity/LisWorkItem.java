/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.lis.domain.workitem.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 检验工单聚合根（表 {@code lis_work_item}，P0 骨架）。
 */
@Getter
@Setter
@Entity
@Table(name = "lis_work_item")
public class LisWorkItem extends BaseAuditedEntity {

    @Column(name = "barcode")
    private String barcode;

    @Column(name = "sample_no")
    private String sampleNo;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "encounter_id")
    private Long encounterId;

    @Column(name = "status_enum", nullable = false, length = 32)
    private String statusEnum;
}