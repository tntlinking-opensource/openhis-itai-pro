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

package com.openhis.health.application.basic.patient.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 患者分页查询请求（患者管理菜单）
 *
 * <p>检索关键字见 {@link AbstractPageQuery} 的 {@code keyword}。</p>
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PatientQueryRequest extends AbstractHealthPageRequest {

}
