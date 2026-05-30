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

package com.openhis.health.application.inpatient.financial.registration.response;

import com.openhis.health.application.inpatient.financial.registration.dto.RegistrationPatientSelectItemDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 住院登记患者分页检索响应（IF-IP-REG-06）。
 *
 * <p>继承 {@link PagedResult}，{@code pageNo} 为 1-based（B.10.11）。</p>
 */
public class PagePatientsForSelectResponse extends PagedResult<RegistrationPatientSelectItemDto> {
}
