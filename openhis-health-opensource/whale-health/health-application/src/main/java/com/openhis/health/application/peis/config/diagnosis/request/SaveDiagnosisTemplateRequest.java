/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.config.diagnosis.request;

import lombok.Data;

/**
 * 保存诊断建议库模板请求（CFG-08）。
 */
@Data
public class SaveDiagnosisTemplateRequest {

    /** 诊断定义主键；更新时必填。 */
    private Long definitionId;

    /** 诊断编码（映射 conditionCode）。 */
    private String code;

    /** 诊断名称（映射 name）。 */
    private String display;

    /** 分类编码（可选）。 */
    private String categoryCode;

    /** 状态：active / inactive（映射 PublicationStatus）。 */
    private String statusEnum;
}
