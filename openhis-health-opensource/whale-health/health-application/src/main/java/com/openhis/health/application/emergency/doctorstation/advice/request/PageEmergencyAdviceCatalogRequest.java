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

package com.openhis.health.application.emergency.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.common.pagination.PageQueryBinder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 急诊医生站医嘱目录分页请求（按 {@code productKind} 路由域目录查询，不经产品读模型）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "急诊医生站-分页检索医嘱目录请求")
public class PageEmergencyAdviceCatalogRequest extends PageQueryBinder.AbstractHealthPageRequest {

    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /**
     * {@link com.openhis.health.application.emergency.doctorstation.advice.constants.EmergencyDoctorStationAdviceConstants}
     * 中 {@code PRODUCT_KIND_*} 常量之一。
     */
    @NotBlank(message = "productKind 不能为空")
    @Schema(description = "产品大类：MEDICATION | DEVICE | ACTIVITY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productKind;

    /** 目录类别编码（可选）。 */
    @Schema(description = "目录类别编码")
    private String categoryCode;

    /** 定价来源码（与门诊医生站目录一致；空则默认零售价口径）。 */
    @Schema(description = "定价来源码")
    private String pricingSourceCode;
}
