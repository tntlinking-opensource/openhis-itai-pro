/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.masterreview.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 保存总检综述请求（IF-PEIS-MAS-03）。
 */
@Data
public class SaveMasterDraftRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 就诊主键。 */
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 综述文本（可选；与结论至少填一）。 */
    @Schema(description = "综述文本")
    private String summaryText;

    /** 结论文本（SDD 主字段）。 */
    @Schema(description = "结论文本", requiredMode = Schema.RequiredMode.REQUIRED)
    private String conclusionText;
}
