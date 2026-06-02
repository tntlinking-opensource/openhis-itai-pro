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

package com.openhis.health.application.inpatient.pharmacy.dispense.response;

import com.openhis.health.application.inpatient.pharmacy.dispense.dto.PractitionerOptionDto;
import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 住院发药页初始化响应（IF-IPP-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院发药页初始化响应")
public class LoadInpatientPharmacyDispensePageInitResponse {

    /** 启用药房机构下拉。 */
    @Schema(description = "启用药房机构选项")
    private List<OptionDto> pharmacyOrganizationOptions;

    /** 发放状态筛选项（{@link com.openhis.health.domain.share.enums.DispenseStatus} code）。 */
    @Schema(description = "发放状态筛选项")
    private List<OptionDto> dispenseStatusFilterOptions;

    /**
     * 未发药原因选项；枚举类未落库前为空列表（与门诊发药一致），作废接口仍提交 {@code notPerformedReasonEnum} 字符串 code。
     */
    @Schema(description = "未发药原因选项")
    private List<OptionDto> notPerformedReasonOptions;

    /** 病区候选（value=病区库位 id）。 */
    @Schema(description = "病区候选（value=病区库位 id）")
    private List<OptionDto> wardOptions;

    /** 调配药师候选分页（Spring {@link Page} 壳，前端按 pageNo/pageSize 解析）。 */
    @Schema(description = "调配药师候选分页")
    private Page<PractitionerOptionDto> preparerCandidatePage;
}
