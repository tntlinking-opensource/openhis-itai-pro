/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.ATDManager.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.shared.inpatient.AdmissionVitalSignSubmitDto;
import com.zxplat.application.inpatient.nursestation.encountermanage.dto.ParticipantSubmitDto;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
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
    /** 操作类型：ASSIGN/TRANSFER/SWAP。 */
    private String operationType;

    /** 发起方就诊主键。 */
    @NotNull(message = "就诊标识不能为空")
    @Schema(description = "发起方就诊主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 目标病房位置主键。 */
    @Schema(description = "目标病房位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long houseLocationId;

    /** 目标床位位置主键。 */
    @Schema(description = "目标床位位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bedLocationId;

    /** 互换对手就诊主键。 */
    @Schema(description = "互换对手就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterIdB;

    /** 病情优先级编码。 */
    @Schema(description = "病情优先级编码")
    private String priorityEnum;

    /** 责任医护提交行。 */
    @Schema(description = "责任医护提交行")
    private List<ParticipantSubmitDto> participants;

    /** 入院体征提交块。 */
    @Schema(description = "入院体征提交块")
    private AdmissionVitalSignSubmitDto admissionVitalSigns;
}
