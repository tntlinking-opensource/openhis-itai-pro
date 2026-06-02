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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.response;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingCatalogRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * IF-IP-PRC-01：划价选品目录分页响应（B.10.11）。
 *
 * <p>行类型 {@link NurseStationPricingCatalogRowDto}：含定价、单位槽、套餐子项、吸氧/心电 Gate 标记。</p>
 */
public class PageNurseStationPricingCatalogResponse extends PagedResult<NurseStationPricingCatalogRowDto> {
}
