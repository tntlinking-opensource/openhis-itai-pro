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

package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 位置物理形式枚举。
 */
@Getter
public enum LocationForm implements EnumInterface {
    /**
     * WARD。
     */
    WARD(0L, "WARD", "病区", "", 4L),
    /**
     * BED。
     */
    BED(0L, "BED", "床位", "", 8L),
    /**
     * HOUSE（病房）。
     */
    HOUSE(0L, "HOUSE", "病房", "", 10L),
    /**
     * PHARMACY。
     */
    PHARMACY(0L, "PHARMACY", "药房", "", 16L),
    /**
     * WAREHOUSE。
     */
    WAREHOUSE(0L, "WAREHOUSE", "仓库", "", 17L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * LocationForm。
     */
    LocationForm(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

}
