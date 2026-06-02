/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.clinical.procedure.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 新建执行记录命令。
 */
@Getter
@Setter
public class CreateProcedureRecordCommand {
    /** 请求主键。 */
    private Long requestId;
    /** 请求来源表。 */
    private String requestTable;
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 类别枚举编码。 */
    private String categoryEnum;
    /** 发生时间。 */
    private LocalDateTime occurrenceTime;
    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 为 {@code true} 时跳过「同 requestId+requestTable 已存在则拒绝」门禁，允许多条执行事实（门诊护士站 BR-NUR-005）。
     * <p>{@code null} 或 {@code false} 时保留开立侧幂等保护（BR-PRO-001）。</p>
     */
    private Boolean skipDuplicateRequestAnchorCheck;
}

