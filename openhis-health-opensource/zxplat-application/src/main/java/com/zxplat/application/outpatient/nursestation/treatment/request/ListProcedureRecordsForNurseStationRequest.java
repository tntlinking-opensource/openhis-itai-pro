/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.treatment.request;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
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
@Schema(description = "分页查询诊疗执行记录请求")
public class ListProcedureRecordsForNurseStationRequest extends AbstractHealthPageRequest {

    /**
     * 就诊主键。
     */
    @NotNull(message = "encounterId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 就诊主键。 */
    private Long encounterId;

    /**
     * 缩窄单条医嘱来源请求。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "缩窄单条医嘱来源请求主键")
    /** 缩窄单条医嘱来源请求主键。 */
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
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "时间范围下限（含）")
    /** 时间范围下限（含）。 */
    private LocalDateTime startTime;

    /**
     * 时间范围上限（含）。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "时间范围上限（含）")
    /** 时间范围上限（含）。 */
    private LocalDateTime endTime;

    /**
     * SDD §3.4 命名页码；若未传 {@link com.zxplat.domain.share.query.AbstractPageQuery#getPageNo()} 则由应用层并入 {@code pageNo}。
     */
    @Schema(description = "页码（与 pageNo 等价，SDD 字段名 pageNum）")
    private Integer pageNum;
}
