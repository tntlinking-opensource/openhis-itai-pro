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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 医嘱补费父医嘱列表行（IF-IP-PRC-03）。
 *
 * <p>展示列与护士站医嘱视图 {@code NurseStationOrderViewDisplayCore} 对齐，供补费弹窗选父医嘱。</p>
 */
@Data
@Accessors(chain = true)
/** 医嘱补费父医嘱列表行。 */
@Schema(description = "医嘱补费父医嘱列表行")
public class NurseStationSupplementParentOrderRowDto {

    @Schema(description = "稳定行键：requestId + SOH(U+0001) + orderType")
    private String listRowKey;

    @Schema(description = "药品/诊疗申请主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;

    /** 父医嘱类型：MED_REQUEST / SERVICE_REQUEST。 */
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

    /** 请求状态 code（须 VERIFIED 或 STOPPED 方可补费）。 */
    @Schema(description = "状态 code")
    private String statusEnum;

    /** 请求状态展示名。 */
    @Schema(description = "状态展示")
    private String statusDisplay;

    /** 长临 code。 */
    @Schema(description = "长临 code")
    private String therapyEnum;

    /** 长临展示名。 */
    @Schema(description = "长临展示")
    private String therapyDisplay;

    /** 医嘱摘要（药品名/诊疗名等）。 */
    @Schema(description = "医嘱摘要")
    private String adviceDisplayName;

    /** 规格（药品行）。 */
    @Schema(description = "规格")
    private String specificationText;

    /** 剂量展示（药品行）。 */
    @Schema(description = "剂量展示")
    private String dosageDisplay;

    /** 用法展示（药品行）。 */
    @Schema(description = "用法展示")
    private String usageDisplay;

    /** 频次展示。 */
    @Schema(description = "频次展示")
    private String frequencyDisplay;

    /** 开立时间。 */
    @Schema(description = "开立时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime authoredTime;

    /** 停嘱时间（停嘱父医嘱仍可补费）。 */
    @Schema(description = "停嘱时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime stopTime;

    /** 开立医生姓名。 */
    @Schema(description = "开立医生")
    private String requesterName;

    /** 执行/发药位置展示。 */
    @Schema(description = "执行/发药位置")
    private String performerLocationDisplay;

    /** 诊疗类目 code（诊疗行）。 */
    @Schema(description = "诊疗类目")
    private String categoryEnum;

    /**
     * 药品成组号（{@code med_medication_request.group_id}；与医生站列表同源；仅药嘱行有效，诊嘱为 null）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品成组号（仅药嘱）")
    private Long groupId;

    @Schema(description = "组号")
    private Integer groupNo;

    @Schema(description = "排序号")
    private Integer sortNumber;
}
