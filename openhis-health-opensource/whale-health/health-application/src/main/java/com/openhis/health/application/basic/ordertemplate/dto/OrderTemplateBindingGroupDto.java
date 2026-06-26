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

package com.openhis.health.application.basic.ordertemplate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 组套模板按绑定维度分段列表（IF-BAS-OTL-07/11）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "组套模板绑定维度分段列表")
public class OrderTemplateBindingGroupDto {

    @Schema(description = "个人组套列表")
    private List<OrderTemplateHeaderRowDto> personalList;

    @Schema(description = "科室组套列表")
    private List<OrderTemplateHeaderRowDto> departmentList;

    /** 全院归属组套/模板头列表。 */
    @Schema(description = "全院组套列表")
    private List<OrderTemplateHeaderRowDto> hospitalList;

    /** 协定处方组套/模板头列表。 */
    @Schema(description = "协定处方组套列表")
    private List<OrderTemplateHeaderRowDto> agreementList;
}
