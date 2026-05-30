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

package com.openhis.health.application.basic.supplier.dto;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 供应商页初始化：类型下拉与机构下拉。
 */
@Data
@Accessors(chain = true)
@Schema(description = "供应商初始化数据传输对象")
/**
 * SupplierInitDto。
 */
public class SupplierInitDto {
    @Schema(description = "供应商类别选项列表")
    /**
     * supplierTypeOptions。
     */
    private List<OptionDto> supplierTypeOptions;

    @Schema(description = "所属机构选项列表（由组织树映射）")
    /**
     * organizationOptions。
     */
    private List<OptionDto> organizationOptions;
}
