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
 * 皮试临床状态（DM-CLN-001 §4.1.1 {@code clinical_status_enum}）。
 */
@Getter
public enum ClinicalStatus implements EnumInterface {

    /** 阳性。 */
    ACTIVE(0L, "active", "阳性", "当前皮试结果为阳性风险状态", 1L),
    /** 阴性。 */
    INACTIVE(0L, "inactive", "阴性", "当前皮试结果为阴性状态", 2L),
    /** 已解决。 */
    RESOLVED(0L, "resolved", "已解决", "已处理或不再持续", 3L),
    /** 未知。 */
    UNKNOWN(0L, "unknown", "未知", "状态不明确或待确认", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    ClinicalStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }
}
