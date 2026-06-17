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
 * 皮试严重程度（DM-CLN-001 §4.1.3 {@code severity_enum}）。
 */
@Getter
public enum AllergySeverity implements EnumInterface {

    /** 轻微。 */
    MILD(0L, "mild", "轻微", "风险或反应程度较轻", 1L),
    /** 中度。 */
    MODERATE(0L, "moderate", "中度", "风险或反应程度中等", 2L),
    /** 严重。 */
    SEVERE(0L, "severe", "严重", "风险或反应程度较重", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    AllergySeverity(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }
}
