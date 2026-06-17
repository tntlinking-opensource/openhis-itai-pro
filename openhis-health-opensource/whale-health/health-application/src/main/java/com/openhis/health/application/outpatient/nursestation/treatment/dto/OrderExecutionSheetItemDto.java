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

package com.openhis.health.application.outpatient.nursestation.treatment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.querysupport.advice.dto.AdviceRequestExtensionSnapshotDto;
import com.openhis.health.module.querysupport.advice.dto.ProcedurePerformerItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 医嘱执行单抽屉行 DTO（IF-NUR-03）。
 */
@Data
@Accessors(chain = true)
/** 医嘱执行单抽屉行。 */
@Schema(description = "医嘱执行单抽屉行")
public class OrderExecutionSheetItemDto {

    /**
     * 执行记录主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行记录主键")
    private Long procedureId;

    /**
     * 来源请求主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "来源请求主键")
    private Long requestId;

    /**
     * 来源表名。
     */
    @Schema(description = "来源表名")
    private String requestTable;

    /**
     * 执行状态持久化码。
     */
    @Schema(description = "执行状态持久化码")
    private String statusEnum;

    /**
     * 执行状态展示文案。
     */
    @Schema(description = "执行状态展示文案")
    private String statusEnumText;

    /**
     * 执行分类持久化码。
     */
    @Schema(description = "执行分类持久化码")
    private String categoryEnum;

    /**
     * 患者主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键")
    private Long patientId;

    /**
     * 就诊主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    private Long encounterId;

    /**
     * 发生时间。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "发生时间")
    private LocalDateTime occurrenceTime;

    /**
     * 记录时间。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "记录时间")
    private LocalDateTime recordedTime;

    /**
     * 记录人主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "记录人主键")
    private Long recorderId;

    /**
     * 结果摘要。
     */
    @Schema(description = "结果摘要")
    private String resultText;

    @Schema(description = "医嘱内容扩展快照")
    private AdviceRequestExtensionSnapshotDto requestExtensions;

    @Schema(description = "执行参与人列表")
    private List<ProcedurePerformerItemDto> performers;
}
