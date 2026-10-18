/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.response;

import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummaryPatientGroupDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 分页查询待汇总明细响应（IF-IP-SUM-01）。
 *
 * <p>分页壳经 {@link com.zxplat.application.shared.pagination.PagedResultBuilder} 装配；
 * 业务数据在 {@link #patientGroups}（发放行维度 total 在父类 {@code totalCount}）。</p>
 */
@Getter
@Setter
/** 分页查询待汇总明细响应。 */
@Schema(description = "分页查询待汇总明细响应")
public class PagePendingDispenseSummaryLinesResponse extends PagedResult<NurseStationDispenseSummaryPatientGroupDto> {

    @Schema(description = "按就诊分组")
    /** 按就诊分组。 */
    private List<NurseStationDispenseSummaryPatientGroupDto> patientGroups;
}
