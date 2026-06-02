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

import com.openhis.health.application.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
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
@Schema(description = "住院登记待办列表分页请求")
public class PageRegistrationQueueRequest extends AbstractHealthPageRequest {

    /** 登记状态筛选：PLANNED / WAIT_ADMISSION / ADMITTED；缺省待登记。 */
    @Schema(description = "登记状态筛选：PLANNED 待登记 / WAIT_ADMISSION 待入科 / ADMITTED 已入科")
    private String registrationListStatus;

    /** 身份证号精确筛选。 */
    @Schema(description = "身份证号精确筛选")
    private String idCard;
}
