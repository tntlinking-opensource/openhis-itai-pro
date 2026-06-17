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

package com.openhis.health.application.inpatient.nursestation.costdetail.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-CDT-01：费用明细页初始化请求。
 *
 * <p>无业务字段；服务端以 {@link java.time.LocalDate#now()} 计算默认日期区间。</p>
 */
@Data
@Accessors(chain = true)
/** 住院护士站费用明细页初始化请求。 */
@Schema(description = "住院护士站费用明细页初始化请求")
public class LoadNurseStationCostDetailPageInitRequest {
}
