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

package com.openhis.health.application.inpatient.doctorstation.encountermanage.request;

import com.openhis.health.application.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 住院医生站在院患者列表分页查询请求（AS-IP-DOR-001 / IF-IP-DOR-01）。
 *
 * <p>分页字段 {@code pageNo}、{@code pageSize}、{@code keyword} 继承自
 * {@link AbstractHealthPageRequest}（B.10.11，{@code pageNo} 为 1-based）。
 * 科室收窄由服务端从登录上下文解析，请求体不传 organizationId。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "住院医生站在院患者列表分页查询请求", requiredProperties = {"pageNo", "pageSize"})
public class PagePatientQueueRequest extends AbstractHealthPageRequest {
}
