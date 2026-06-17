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

package com.openhis.health.application.inpatient.financial.registration.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提交住院登记响应。
 */
@Data
@Accessors(chain = true)
/** 提交住院登记结果。 */
@Schema(description = "提交住院登记结果")
public class SubmitRegistrationResponse {

    @Schema(description = "住院就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 住院就诊 ID（业务字段）。 */
    private Long encounterId;

    /** 住院号。 */
    @Schema(description = "住院号")
    /** 住院号（业务字段）。 */
    private String encounterBusNo;

    /** 登记后状态 code。 */
    @Schema(description = "登记后状态 code（REGISTERED，待护士分床）")
    /** 登记后状态 code（REGISTERED，待护士分床）（业务字段）。 */
    private String statusEnum;

    /** 预交金支付单 ID；未收款为 null。 */
    @Schema(description = "预交金支付单 ID；未收款为 null")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 预交金支付单 ID；未收款为 null（业务字段）。 */
    private Long paymentId;

    /** 是否本次新开账户。 */
    @Schema(description = "是否本次新开账户")
    /** 是否本次新开账户（业务字段）。 */
    private Boolean accountOpened;

    /** 医保就诊 ID。 */
    @Schema(description = "医保就诊 ID（2401 成功时）")
    /** 医保就诊 ID（2401 成功时）（业务字段）。 */
    private String ybMdtrtId;
}
