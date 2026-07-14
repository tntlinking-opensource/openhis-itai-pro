/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderexecute.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 批量不执行响应（IF-IP-EXE-04）。
 *
 * <p>{@code processedCount} 为成功登记 STOPPED 的槽位数；失败时整批回滚。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量不执行响应")
public class SkipNurseStationOrderSlotsResponse {

    @Schema(description = "成功登记的槽位数")
    /** 成功登记的槽位数。 */
    private Integer processedCount;
}
