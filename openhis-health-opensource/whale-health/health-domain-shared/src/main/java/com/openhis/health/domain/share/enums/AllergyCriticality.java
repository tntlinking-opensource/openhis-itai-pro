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
 * 皮试危险程度（DM-CLN-001 §4.1.4 {@code criticality_enum}）。
 */
@Getter
public enum AllergyCriticality implements EnumInterface {

    /** 低。 */
    LOW(0L, "low", "低", "危险程度低", 1L),
    /** 高。 */
    HIGH(0L, "high", "高", "危险程度高", 2L),
    /** 无法评估。 */
    UNASSESSABLE(0L, "unassessable", "无法评估", "暂无法评估危险程度", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    AllergyCriticality(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }
}
