/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.encountermanage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 队列行展示增补：读模型未投影字段由 App 批量回填。
 */
@Data
@Accessors(chain = true)
@Schema(description = "队列展示增补字段")
public class NurseStationQueueDisplayEnrichment {

    @Schema(description = "病房名称")
    private String houseName;

    @Schema(description = "拼音码")
    private String pinyinCode;

    /** 五笔检索码。 */
    @Schema(description = "五笔码")
    private String wubiCode;
}
