/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderexecute.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 批量执行响应（IF-IP-EXE-02）。
 *
 * <p>{@code processedCount} 为本次同事务内成功完成的槽位数；任一条失败则整批回滚，计数为 0。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量执行响应")
public class ExecuteNurseStationOrdersResponse {

    @Schema(description = "成功处理的槽位数")
    /** 成功处理的槽位数。 */
    private Integer processedCount;
}
