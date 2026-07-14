/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
