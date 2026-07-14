/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdefinition.queries.model;

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
    /** manufacturer文本。 */
    private String manufacturerText;
}
