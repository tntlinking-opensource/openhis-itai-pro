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

package com.openhis.health.application.inpatient.pharmacy.returnmedicine.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * 加载住院退药页初始化元数据响应（IF-IPR-01）。
 */
@Data
@Accessors(chain = true)
/** 加载住院退药页初始化元数据响应。 */
@Schema(description = "加载住院退药页初始化元数据响应")
public class LoadInpatientReturnMedicineInitResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "管辖内启用药房下拉；value 为 locationId")
    private List<OptionDto> pharmacyOrganizationOptions;

    /** 病区候选下拉；value 为 ward locationId。 */
    @Schema(description = "病区候选下拉；value 为 ward locationId")
    private List<OptionDto> wardOptions;

    /** 退药状态下拉：PENDING_REFUND / REFUNDED。 */
    @Schema(description = "退药状态下拉：PENDING_REFUND / REFUNDED")
    private List<OptionDto> refundStatusOptions;
}
