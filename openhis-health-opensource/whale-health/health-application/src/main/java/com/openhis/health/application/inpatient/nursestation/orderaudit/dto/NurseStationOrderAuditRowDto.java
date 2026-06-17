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

package com.openhis.health.application.inpatient.nursestation.orderaudit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 医嘱校对列表行（IF-IP-AUD-01，BR-IP-AUD-006）。
 */
@Data
@Accessors(chain = true)
/** 医嘱校对列表行。 */
@Schema(description = "医嘱校对列表行")
public class NurseStationOrderAuditRowDto {

    @Schema(description = "稳定行键：requestId + SOH(U+0001) + orderType")
    private String listRowKey;

    @Schema(description = "药品/诊疗申请主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;

    /** 医嘱类型：MED_REQUEST / SERVICE_REQUEST。 */
    @Schema(description = "医嘱类型：MED_REQUEST / SERVICE_REQUEST")
    private String orderType;

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者主键。 */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 请求状态 code。 */
    @Schema(description = "请求状态 code")
    private String statusEnum;

    /** 请求状态中文展示。 */
    @Schema(description = "请求状态中文展示")
    private String statusDisplay;

    /** 长临 code。 */
    @Schema(description = "长临 code")
    private String therapyEnum;

    /** 长临展示。 */
    @Schema(description = "长临展示")
    private String therapyDisplay;

    /** 医嘱名称摘要（BR-IP-AUD-006）。 */
    @Schema(description = "医嘱名称摘要")
    private String adviceDisplayName;

    /** 规格/大小；诊疗可为 null。 */
    @Schema(description = "规格/大小；诊疗可为 null")
    private String specificationText;

    /** 剂量/数量展示。 */
    @Schema(description = "剂量/数量展示")
    private String dosageDisplay;

    /** 用法展示（药品）。 */
    @Schema(description = "用法展示（药品）")
    private String usageDisplay;

    /** 频次展示。 */
    @Schema(description = "频次展示")
    private String frequencyDisplay;

    /** 开立时间。 */
    @Schema(description = "开立时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime authoredTime;

    /** 停嘱时间；未停为 null。 */
    @Schema(description = "停嘱时间；未停为 null")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime stopTime;

    /** 开立医生姓名。 */
    @Schema(description = "开立医生姓名")
    private String requesterName;

    /** 执行/发药位置展示。 */
    @Schema(description = "执行/发药位置展示")
    private String performerLocationDisplay;

    /** 诊疗类目（转科/出院等）；药品行 null。 */
    @Schema(description = "诊疗类目；药品行 null")
    private String categoryEnum;

    /**
     * 药品成组号（{@code med_medication_request.group_id}；同组前端相邻展示；仅药嘱行有效，诊嘱为 null）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品成组号（仅药嘱）")
    private Long groupId;

    @Schema(description = "药品组号/排序号（排序用）")
    private Integer groupNo;

    @Schema(description = "排序号（药品）")
    private Integer sortNumber;
}
