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

package com.openhis.health.application.inpatient.nursestation.orderexecute.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 医嘱执行列表行（IF-IP-EXE-01，BR-IP-EXE-009/012）。
 *
 * <p>每行对应一条 VERIFIED 药/诊医嘱；四类槽位字段同构，列表按 {@code executeTab} 仅回传
 * 当前 Tab 对应类别且 {@code planTime ≤ executionDeadline} 的计划点，其余三类为空数组；
 * 医嘱展示字段与 bindItems 不因 Tab 切换而变化。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱执行列表行")
public class NurseStationOrderExecuteRowDto {

    /** 稳定行键：orderId + SOH + orderType。 */
    @Schema(description = "稳定行键")
    private String listRowKey;

    /** 医嘱/申请主键。 */
    @Schema(description = "医嘱/申请主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** 医嘱类型：MED_REQUEST / SERVICE_REQUEST。 */
    @Schema(description = "医嘱类型")
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

    /** 请求状态展示。 */
    @Schema(description = "请求状态展示")
    private String statusEnumText;

    /** 长临 code。 */
    @Schema(description = "长临 code")
    private String therapyEnum;

    /** 长临展示。 */
    @Schema(description = "长临展示")
    private String therapyEnumText;

    /** 医嘱展示摘要。 */
    @Schema(description = "医嘱展示摘要")
    private String adviceDisplayName;

    /** 规格展示。 */
    @Schema(description = "规格展示")
    private String specificationText;

    /** 剂量/数量展示。 */
    @Schema(description = "剂量/数量展示")
    private String dosageDisplay;

    /** 用法展示。 */
    @Schema(description = "用法展示")
    private String usageDisplay;

    /** 频次展示。 */
    @Schema(description = "频次展示")
    private String frequencyDisplay;

    /** 开立时间。 */
    @Schema(description = "开立时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime authoredTime;

    /** 停嘱时间。 */
    @Schema(description = "停嘱时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime stopTime;

    /** 开立医生姓名。 */
    @Schema(description = "开立医生姓名")
    private String requesterName;

    /**
     * 药品成组号（{@code med_medication_request.group_id}；同组前端相邻展示；仅药嘱行有效，诊嘱为 null）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品成组号（仅药嘱）")
    private Long groupId;

    /** 待执行槽位。 */
    @Schema(description = "待执行槽位")
    private List<NurseStationOrderExecuteSlotDto> pendingSlots;

    /** 已执行槽位。 */
    @Schema(description = "已执行槽位")
    private List<NurseStationOrderExecuteSlotDto> executedSlots;

    /** 不执行槽位。 */
    @Schema(description = "不执行槽位")
    private List<NurseStationOrderExecuteSlotDto> skippedSlots;

    /** 已取消执行槽位。 */
    @Schema(description = "已取消执行槽位")
    private List<NurseStationOrderExecuteSlotDto> cancelledSlots;

    /** 绑定项；无模板时为 []。 */
    @Schema(description = "绑定项")
    private List<NurseStationOrderExecuteBindItemDto> bindItems;
}
