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

package com.openhis.health.application.inpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceDraftLineRequest;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-14：暂存医嘱变更批次请求（多行 CREATE/UPDATE/DELETE 同事务）。
 *
 * <p>单行载荷见 {@link InpatientDoctorStationAdviceDraftLineRequest}；失败整批回滚。
 * 临时药品/诊疗行在 AppService 内挂自费账户费用草稿（与门诊一致，发放仅在签发 FIFO）；长期药品仅 Request（BR-IP-ORD-005）。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-暂存医嘱变更批次请求")
public class SaveInpatientDoctorStationAdviceDraftBatchRequest {

    /** 当前就诊上下文，与读模型门控一致。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_ENCOUNTER_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 必须与就诊患者一致，由 AppService 与领域二次校验。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_PATIENT_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 租户定价策略码，见 {@link com.openhis.health.domain.share.constants.DoctorStationAdviceConstants} 定价来源常量族。 */
    @Schema(description = "定价来源码；空则回落默认口径")
    private String pricingSourceCode;

    /** 本批次 CREATE/UPDATE/DELETE 行集合，顺序即处理建议顺序。 */
    @NotEmpty(message = DoctorStationAdviceConstants.VALIDATION_DRAFT_LINES_REQUIRED)
    @Valid
    @Schema(description = "批次行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<InpatientDoctorStationAdviceDraftLineRequest> lines;
}
