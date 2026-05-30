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

package com.openhis.health.application.inpatient.nursestation.ATDManager.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.ParticipantSubmitDto;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-NUR-ATD-02：分床/换床/互换提交入参。
 */
@Data
@Accessors(chain = true)
@Schema(description = "分床换床互换请求", requiredProperties = {"operationType", "encounterId"})
public class ExecuteBedOperationRequest {

    /**
     * 写操作类型：{@link InpatientNurseStationConstants#BED_OPERATION_ASSIGN} /
     * {@link InpatientNurseStationConstants#BED_OPERATION_TRANSFER} /
     * {@link InpatientNurseStationConstants#BED_OPERATION_SWAP}。
     */
    @NotBlank(message = "操作类型不能为空")
    @Schema(description = "操作类型：ASSIGN/TRANSFER/SWAP", required = true)
    private String operationType;

    /** 当前选中患者就诊主键（发起方）。 */
    @NotNull(message = "就诊标识不能为空")
    @Schema(description = "发起方就诊主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 目标病房位置主键（ASSIGN/TRANSFER 必填）。 */
    @Schema(description = "目标病房位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long houseLocationId;

    /** 目标床位位置主键（ASSIGN/TRANSFER 必填）。 */
    @Schema(description = "目标床位位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bedLocationId;

    /** 互换对手就诊主键（SWAP 必填）。 */
    @Schema(description = "互换对手就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterIdB;

    /** 病情优先级 code（ASSIGN 可选，写入就诊主档）。 */
    @Schema(description = "病情优先级编码")
    private String priorityEnum;

    /** 责任医护（ASSIGN 建议必填，全量收口后 INSERT）。 */
    @Schema(description = "责任医护提交行")
    private List<ParticipantSubmitDto> participants;
}
