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

package com.openhis.health.application.outpatient.financial.registration.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 挂号列表分页请求。分页与关键字见 {@link AbstractPageQuery}（B.10.11）；日期区间为挂号业务日（LocalDate）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
/** 挂号列表分页请求。 */
@Schema(description = "挂号列表分页请求")
public class PageClinicRegistersRequest extends AbstractHealthPageRequest {

    @Schema(description = "开始日期")
    /** 开始日期（业务字段）。 */
    private LocalDate dateFrom;

    /** 查询结束日期。 */
    @Schema(description = "结束日期")
    /** 结束日期（业务字段）。 */
    private LocalDate dateTo;
}
