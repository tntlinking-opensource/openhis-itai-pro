/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 药品目录模板查询模型。
 */
@Getter
@Setter
public class MedicationDefinitionTemplateQueryModel {
    /** 模板名称。 */
    private String templateName;
    /** 模板版本。 */
    private String templateVersion;
    /** 模板文件内容（二进制）。 */
    private byte[] content;
}
