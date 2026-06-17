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

import com.openhis.health.module.querysupport.procedure.dto.NurseStationOrderExecuteBindItemDto;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 医嘱执行写路径槽位（IF-IP-EXE-02/04）。
 *
 * <p>前端从列表 pending 槽位勾选后回传；{@code planTime} 须与列表一致（BR-IP-EXE-023）。
 * {@code bindItems} 为列表 IF-IP-EXE-01 下发的绑定项子集，执行时按勾选派生写路径（BR-IP-EXE-020）。</p>
 */
@Data
@Accessors(chain = true)
/** 医嘱执行写路径槽位。 */
@Schema(description = "医嘱执行写路径槽位", requiredProperties = {"orderId", "orderType", "planTime", "accountId"})
public class NurseStationOrderExecuteWriteSlotDto {

    /** 医嘱/申请主键。 */
    @NotNull(message = "医嘱标识不能为空")
    @Schema(description = "医嘱/申请主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** 医嘱类型：MED_REQUEST / SERVICE_REQUEST。 */
    @NotBlank(message = "医嘱类型不能为空")
    /** 医嘱类型：MED_REQUEST / SERVICE_REQUEST。 */
    @Schema(description = "医嘱类型：MED_REQUEST / SERVICE_REQUEST", required = true)
    private String orderType;

    /** 计划执行时刻（须与列表 pending 槽位一致，BR-IP-EXE-023）。 */
    @NotNull(message = "计划执行时刻不能为空")
    /** 计划执行时刻。 */
    @Schema(description = "计划执行时刻", required = true)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planTime;

    /** 护士实际执行时刻（IF-IP-EXE-02 必填；不执行接口忽略）。 */
    @Schema(description = "护士实际执行时刻")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualTime;

    /**
     * 就诊默认自费账户主键（由 IF-IP-NUR-02 患者列表或 IF-IP-EXE-01 分组回传；IF-IP-EXE-02 执行必填）。
     */
    @NotNull(message = "就诊账户标识不能为空")
    @Schema(description = "就诊默认自费账户主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    @Schema(description = "列表回传的绑定项")
    private List<NurseStationOrderExecuteBindItemDto> bindItems;
}
