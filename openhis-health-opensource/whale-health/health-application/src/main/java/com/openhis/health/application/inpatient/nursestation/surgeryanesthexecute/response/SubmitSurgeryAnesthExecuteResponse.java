/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.surgeryanesthexecute.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 提交手术/麻醉执行响应（IF-IP-SAE-02）。
 *
 * <p>计费主路径委托 EXE-02，字段口径与 {@code ExecuteNurseStationOrdersResponse} 对齐。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交手术/麻醉执行响应")
public class SubmitSurgeryAnesthExecuteResponse {

    /** 成功处理的槽位数（本 IF 单槽，成功为 1）。 */
    @Schema(description = "成功处理的槽位数")
    private Integer processedCount;

    /**
     * 软提示文案（透传 EXE-02 warnings；无则空列表）。
     */
    @Schema(description = "软提示文案；无则空列表")
    private List<String> warnings;
}
