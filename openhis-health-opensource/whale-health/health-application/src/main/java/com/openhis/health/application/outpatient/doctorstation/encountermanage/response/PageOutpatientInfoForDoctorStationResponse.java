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

package com.openhis.health.application.outpatient.doctorstation.encountermanage.response;

import com.openhis.health.application.outpatient.doctorstation.encountermanage.dto.OutpatientInfoItemDto;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 医生站分页查询门诊就诊患者列表响应（IF-DOR-01）。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为数据列表。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "医生站-分页查询就诊患者列表响应")
public class PageOutpatientInfoForDoctorStationResponse extends PagedResult<OutpatientInfoItemDto> {

    /**
     * 费用性质（合同）下拉项：与请求中 {@code organizationId} 对齐的机构维度全量主数据；
     * {@code value} 为合同主键字符串，{@code label} 为名称，{@code sort} 为展示序，{@code busNo} 为业务编号。
     */
    @Schema(description = "费用性质下拉（与当前列表请求科室所属机构一致）")
    private List<OptionDto> feeNatureOptions;
}
