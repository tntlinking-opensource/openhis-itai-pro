/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.nursestation.patient.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 分页查询门诊护士站患者队列请求（IF-NUR-01；B.10.11）。
 *
 * <p>继承 {@link AbstractHealthPageRequest}，统一分页契约：
 * {@code pageNo} 为 1-based，{@code pageSize} 经边界防护，{@code keyword} 为可选检索关键字。</p>
 *
 * <p><strong>注意</strong>：机构收窄由应用层 {@link com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver#resolveOrganizationUnitIdForQuery()}
 * 解析后传入查询服务，请求中禁止承载 organizationId（B.10.11）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "分页查询门诊护士站患者队列请求（B.10.11：机构由 CurrentManagedOrganizationIdsResolver 在应用层收窄）")
public class PageNurseStationPatientQueueRequest extends AbstractHealthPageRequest {

    /**
     * 队列类型筛选（可选）。
     */
    @Schema(description = "队列类型筛选")
    private String queueType;
}
