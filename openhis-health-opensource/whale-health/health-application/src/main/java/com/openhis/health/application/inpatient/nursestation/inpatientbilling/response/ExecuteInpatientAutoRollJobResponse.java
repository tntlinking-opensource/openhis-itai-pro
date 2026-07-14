/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.response;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * IF-IP-ARG-08：住院滚费定时任务响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院滚费定时任务响应")
public class ExecuteInpatientAutoRollJobResponse {

    /** 滚费业务日。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "滚费业务日")
    private LocalDate billDate;

    /** 扫描就诊数。 */
    @Schema(description = "扫描就诊数")
    private Integer scannedEncounterCount;

    /** 成功处理就诊数。 */
    @Schema(description = "成功处理就诊数")
    private Integer processedEncounterCount;

    /** 新建费用条数。 */
    @Schema(description = "新建费用条数")
    private Integer createdChargeItemCount;

    /** 跳过条数。 */
    @Schema(description = "跳过条数")
    private Integer skippedCount;

    /** 失败条数。 */
    @Schema(description = "失败条数")
    private Integer failedCount;
}
