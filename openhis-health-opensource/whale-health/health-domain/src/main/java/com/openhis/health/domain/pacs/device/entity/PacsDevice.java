/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.device.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 检查设备台账（{@code pacs_device}）。
 *
 * <p>维护名称、模态、DICOM AE Title 与启停，供登记/执行/MWL 按模态选用（DM-PACS-005）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "pacs_device")
public class PacsDevice extends BaseAuditedEntity {

    /** 业务编码（≈ SBID）；租户内未软删唯一。 */
    @Column(name = "device_code", nullable = false)
    private String deviceCode;

    /** 设备名称（≈ SBMC）。 */
    @Column(name = "name", nullable = false)
    private String name;

    /** 模态码（≈ MODALITY / SBLX）。 */
    @Column(name = "modality_code", nullable = false)
    private String modalityCode;

    /** DICOM AE Title（≈ MODAET）。 */
    @Column(name = "ae_title")
    private String aeTitle;

    /** 机房/室名。 */
    @Column(name = "station_name")
    private String stationName;

    /** 是否启用。 */
    @Column(name = "enabled_flag", nullable = false)
    private Boolean enabledFlag = Boolean.TRUE;

    /** 列表排序。 */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 备注。 */
    @Column(name = "remark")
    private String remark;
}
