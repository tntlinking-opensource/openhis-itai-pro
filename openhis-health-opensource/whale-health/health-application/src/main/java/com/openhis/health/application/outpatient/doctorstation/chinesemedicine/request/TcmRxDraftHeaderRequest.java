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

package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.request;

import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.dto.TcmRxDraftLineRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * IF-DOR-36：中药处方暂存方头请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "中药处方暂存方头")
public class TcmRxDraftHeaderRequest {

    /** 前端方标识。 */
    @NotBlank(message = "prescriptionGroupKey 不能为空")
    @Schema(description = "前端方标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private String prescriptionGroupKey;

    /** 绑定的中医病证组号。 */
    @NotBlank(message = "中医病证组号不能为空")
    @Schema(description = "绑定的中医病证组号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tcmSyndromeGroup;

    /** 中药付数。 */
    @NotNull(message = "付数不能为空")
    @Schema(description = "中药付数", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal chineseHerbsDoseQuantity;

    /** 代煎标志。 */
    @Schema(description = "代煎标志")
    private Boolean sufferingFlag;

    /** 用法编码。 */
    @Schema(description = "用法编码")
    private String methodCode;

    /** 用药频次编码（{@code rate_code}；暂存/签发写入方内各行）。 */
    @Schema(description = "用药频次编码")
    private String rateCode;

    /** 用药天数。 */
    @Schema(description = "用药天数")
    private Integer days;

    /** 煎煮法编码。 */
    @Schema(description = "煎煮法编码")
    private String decoctionMethodCode;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** 方内明细行。 */
    @NotEmpty(message = "处方明细不能为空")
    @Valid
    @Schema(description = "方内明细行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<TcmRxDraftLineRequest> lines;
}
