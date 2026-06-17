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

package com.openhis.health.application.outpatient.nursestation.treatment.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 分页查询诊疗执行记录（执行记录抽屉）请求（IF-NUR-03）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
/** 分页查询诊疗执行记录请求。 */
@Schema(description = "分页查询诊疗执行记录请求")
public class ListProcedureRecordsForNurseStationRequest extends AbstractHealthPageRequest {

    /**
     * 就诊主键。
     */
    @NotNull(message = "encounterId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /**
     * 缩窄单条医嘱来源请求。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "缩窄单条医嘱来源请求主键")
    private Long requestId;

    /**
     * 执行状态过滤（EventStatus code）。
     */
    @Schema(description = "执行状态过滤（EventStatus code）")
    private String statusEnum;

    /**
     * 执行分类过滤。
     */
    @Schema(description = "执行分类过滤")
    private String categoryEnum;

    /**
     * 时间范围下限（含）。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "时间范围下限（含）")
    private LocalDateTime startTime;

    /**
     * 时间范围上限（含）。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "时间范围上限（含）")
    private LocalDateTime endTime;

    /**
     * SDD §3.4 命名页码；若未传 {@link com.openhis.health.domain.share.query.AbstractPageQuery#getPageNo()} 则由应用层并入 {@code pageNo}。
     */
    @Schema(description = "页码（与 pageNo 等价，SDD 字段名 pageNum）")
    private Integer pageNum;
}
