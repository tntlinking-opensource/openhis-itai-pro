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

package com.openhis.health.application.inpatient.pharmacy.returnmedicine.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 住院确认退药请求（IF-IPR-04）。
 */
@Data
/** 住院确认退药请求。 */
@Schema(description = "住院确认退药请求", requiredProperties = {"items"})
public class ConfirmInpatientMedicineReturnRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "待确认退药发放行列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请选择要退药的药品")
    @Valid
    private List<InpatientReturnMedicineItemRequest> items;

    /** 退药说明，写入 support_info。 */
    @Schema(description = "退药说明，写入 support_info")
    private String reason;

    /** @deprecated CHS 模块启用即上传，此开关不再生效。 */
    @Deprecated
    /** 退药成功后是否上传医保进销存。 */
    @Schema(description = "退药成功后是否上传医保进销存")
    private Boolean medicareUploadEnabled;
}
