/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院自费转医保请求（IF-IP-REG-07）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院自费转医保请求")
public class SwitchInpatientToInsuranceRequest {

    /** 住院就诊 ID。 */
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 目标医保费用性质（合同）ID。 */
    @Schema(description = "目标医保费用性质（合同）ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 主治医生医务人员 ID（2401 须传 atddr_no）。 */
    @Schema(description = "主治医生医务人员 ID（医保登记必填）", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long attendingDoctorId;

    /** 操作人 ID。 */
    @Schema(description = "操作人 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    /** 就诊凭证类型（医保读卡）。 */
    @Schema(description = "就诊凭证类型（医保读卡）")
    private String ybMediumType;

    /** 就诊凭证编号（医保读卡）。 */
    @Schema(description = "就诊凭证编号（医保读卡）")
    private String ybCardInfo;

    /** 人员编号（1101）。 */
    @Schema(description = "人员编号（1101）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String psnNo;

    /** 参保地医保区划。 */
    @Schema(description = "参保地医保区划")
    private String insuplcAdmdvs;

    /** 险种类型。 */
    @Schema(description = "险种类型")
    private String insutype;

    /** 医疗类别；缺省由服务端补 21 普通住院。 */
    @Schema(description = "医疗类别，默认 21 普通住院")
    private String medType;
}
