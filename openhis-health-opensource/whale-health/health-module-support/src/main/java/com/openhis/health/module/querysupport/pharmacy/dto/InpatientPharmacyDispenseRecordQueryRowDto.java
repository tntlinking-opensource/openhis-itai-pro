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

package com.openhis.health.module.querysupport.pharmacy.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 住院发退药记录平铺行（querysupport 中性 DTO；扩展住院号/病区/床号）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class InpatientPharmacyDispenseRecordQueryRowDto extends PharmacyDispenseRecordQueryRowDto {

    /** 住院号。 */
    private String inpatientNo;

    /** 病区名称。 */
    private String wardName;

    /** 床号。 */
    private String bedName;
}
