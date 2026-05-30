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

package com.openhis.health.application.outpatient.pharmacy.returnmedicine.request;

import com.openhis.health.application.common.pagination.PageQueryBinder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 加载门诊药房退药初始化元数据请求（IF-PHA-02-01）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "加载门诊药房退药初始化元数据请求")
public class LoadReturnMedicineInitRequest extends PageQueryBinder.AbstractHealthPageRequest {
    private static final long serialVersionUID = 1L;
}
