/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.ATDManager.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-NUR-ATD-08：取消办理出院响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "取消办理出院响应")
public class CancelInpatientNurseDischargeResponse {

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean succeeded;

    /**
     * 取消后就诊状态码。
     *
     * <p>{@code ON_HOLD}=待出院；{@code IN_PROGRESS}=已入科（出院嘱已撤尽时）。</p>
     */
    @Schema(description = "取消后就诊状态码（ON_HOLD / IN_PROGRESS）")
    private String restoredStatusEnum;
}
