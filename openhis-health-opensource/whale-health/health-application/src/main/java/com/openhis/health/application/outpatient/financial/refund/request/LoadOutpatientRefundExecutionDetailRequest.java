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

package com.openhis.health.application.outpatient.financial.refund.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 加载就诊待执行退费明细请求（IF-REF-03）。
 */
@Data
@Accessors(chain = true)
/** 加载就诊待执行退费明细请求。 */
@Schema(description = "加载就诊待执行退费明细请求")
public class LoadOutpatientRefundExecutionDetailRequest {

    /** 就诊主键。 */
    @NotNull(message = "encounterId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 就诊主键（业务字段）。 */
    private Long encounterId;

    /**
     * 明细展示口径（对齐 {@link com.openhis.health.domain.share.constants.OutpatientRefundQueueTab}）。
     *
     * <p>{@code PENDING}：待执行拟退（{@code REFUNDING}）；{@code SETTLED}：已退费终态（{@code REFUNDED}）；
     * {@code ALL}：二者合并；空则按 {@code PENDING}。</p>
     */
    @Schema(description = "明细展示口径 PENDING/SETTLED/ALL，空则 PENDING")
    /** 明细展示口径 PENDING/SETTLED/ALL，空则 PENDING（业务字段）。 */
    private String detailViewCode;
}
