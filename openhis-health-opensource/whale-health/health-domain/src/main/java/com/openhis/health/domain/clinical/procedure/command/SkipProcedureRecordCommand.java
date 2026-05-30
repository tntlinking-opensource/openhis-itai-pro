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
 * 登记计划点不执行命令（护士站 IF-IP-EXE-04 → Procedure STOPPED）。
 */
@Getter
@Setter
public class SkipProcedureRecordCommand {
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
    /** 计划执行时刻（occurrence_time）。 */
    private LocalDateTime occurrenceTime;
    /** 登记时刻（recorded_time）。 */
    private LocalDateTime recordedTime;
    /** 操作人主键。 */
    private Long operatorId;
    /** 不执行原因（可选）。 */
    private String statusReasonText;
    /**
     * 为 {@code true} 时跳过同 request 锚点重复门禁，允许多计划点（BR-IP-EXE-015）。
     */
    private Boolean skipDuplicateRequestAnchorCheck;
}
