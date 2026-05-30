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

package com.openhis.health.domain.clinical.procedure.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 执行记录查询模型。
 */
@Getter
@Setter
public class ProcedureRecordQueryModel {
    /** 主键。 */
    private Long id;
    /** 请求主键。 */
    private Long requestId;
    /** 请求来源表。 */
    private String requestTable;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 记录时间。 */
    private LocalDateTime recordedTime;
    /**
     * 取消执行关联标识：仅 {@code CANCEL} 行有意义，指向被抵消的已完成执行记录主键（{@code cln_procedure.id}）。
     */
    private Long refundId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 执行分类枚举编码。 */
    private String categoryEnum;
    /** 发生时间。 */
    private LocalDateTime occurrenceTime;
    /** 记录人主键。 */
    private Long recorderId;
    /** 结果摘要。 */
    private String resultText;
}
