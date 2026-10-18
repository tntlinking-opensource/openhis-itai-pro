/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 药品目录导出行查询模型。
 */
@Getter
@Setter
public class MedicationDefinitionExportRowQueryModel {
    /** 业务号。 */
    private String busNo;
    /** 名称。 */
    private String name;
    /** 类别编码。 */
    private String categoryCode;
    /** 状态枚举编码。 */
    private String statusEnum;
    /**
     * 生产厂家展示名（非持久化列；导出时由 AppService 按 manufacturerId 解析后回填）。
     */
    private String manufacturerText;
}
