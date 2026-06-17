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

package com.openhis.health.application.outpatient.nursestation.patient.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
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
 * <p><strong>注意</strong>：机构收窄通过 {@link com.openhis.whale.core.infrastructure.security.ICurrentUser}
 * 在查询服务层完成，请求中禁止承载 organizationId（B.10.11）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
/** 分页查询门诊护士站患者队列请求。 */
@Schema(description = "分页查询门诊护士站患者队列请求（B.10.11：机构由 ICurrentUser 在查询层收窄）")
public class PageNurseStationPatientQueueRequest extends AbstractHealthPageRequest {

    /**
     * 队列类型筛选（可选）。
     */
    @Schema(description = "队列类型筛选")
    private String queueType;
}
