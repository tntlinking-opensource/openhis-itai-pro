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

package com.openhis.health.application.outpatient.pharmacy.dispense.response;

import com.openhis.health.application.outpatient.pharmacy.dispense.dto.PractitionerOptionDto;
import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 门诊发药页初始化响应（IF-OPP-01）。
 *
 * <p>下拉项统一 {@link com.openhis.whale.core.application.dto.OptionDto}；药师候选行为扩展列 {@link com.openhis.health.application.outpatient.pharmacy.dispense.dto.PractitionerOptionDto}。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊发药页初始化响应")
public class LoadPharmacyDispensePageInitResponse {

    /** 当前租户下可选药房机构树（扁平为选项列表）。 */
    @Schema(description = "启用药房机构选项")
    private List<OptionDto> pharmacyOrganizationOptions;

    /** 发放状态筛选下拉（枚举术语）。 */
    @Schema(description = "发放状态筛选项")
    private List<OptionDto> dispenseStatusFilterOptions;

    /** 就诊类别筛选下拉。 */
    @Schema(description = "就诊类别选项")
    private List<OptionDto> encounterClassOptions;

    /** 未发药/作废原因下拉（术语未就绪时可为空列表）。 */
    @Schema(description = "未发药原因选项（目标态枚举未发布时可为空）")
    private List<OptionDto> notPerformedReasonOptions;

    /** 调配药师分页候选（含机构上下文列）。 */
    @Schema(description = "调配药师候选分页")
    private Page<PractitionerOptionDto> preparerCandidatePage;
}
