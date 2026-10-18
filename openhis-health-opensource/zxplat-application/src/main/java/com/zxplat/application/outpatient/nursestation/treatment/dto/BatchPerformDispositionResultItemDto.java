/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.treatment.dto;

import com.zxplat.application.outpatient.nursestation.treatment.response.BatchPerformDispositionResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 批量处置逐行结果占位 DTO（AS-SDD §2.1 包结构）。
 * <p>当前 {@link BatchPerformDispositionResponse} 仅以 id 列表聚合结果；逐行明细字段待 AS 扩展后再落库到本类，避免与未定契约冲突。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量处置逐行结果（预留扩展，暂无业务字段）")
public class BatchPerformDispositionResultItemDto {
}
