/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院医保转自费响应（IF-IP-REG-09）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医保转自费响应")
public class SwitchInpatientToSelfPayResponse {

    /** 是否已完成本地切户。平台撤销失败时不会返回成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 本次从医保户改挂到个人现金户的未结费用行数。 */
    @Schema(description = "本次切换至个人现金账户的收费行数")
    private Integer switchedChargeItemCount;

    /** 承接未结费用的个人现金账户主键。预交金余额仍在此户。 */
    @Schema(description = "个人现金挂账账户 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long selfPayAccountId;
}
