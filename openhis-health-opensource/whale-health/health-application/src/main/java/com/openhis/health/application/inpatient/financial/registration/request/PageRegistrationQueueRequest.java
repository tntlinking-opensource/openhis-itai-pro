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

package com.openhis.health.application.inpatient.financial.registration.request;

import java.util.List;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 住院登记待办列表分页请求（IF-IP-REG-01）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
/** 住院登记待办列表分页请求。 */
@Schema(description = "住院登记待办列表分页请求")
public class PageRegistrationQueueRequest extends AbstractHealthPageRequest {

    @Schema(description = "登记状态筛选（多选）：PLANNED 待登记 / WAIT_ADMISSION 待入科 / ADMITTED 已入科等")
    /** 登记状态筛选（多选）（业务字段）。 */
    private List<String> registrationListStatuses;

    /** 登记状态筛选（单选，兼容旧入参；与 registrationListStatuses 二选一）。 */
    @Schema(description = "登记状态筛选（单选，兼容旧入参）")
    /** 登记状态筛选（单选）（业务字段）。 */
    private String registrationListStatus;

    /** 身份证号精确筛选。 */
    @Schema(description = "身份证号精确筛选")
    /** 身份证号精确筛选（业务字段）。 */
    private String idCard;
}
