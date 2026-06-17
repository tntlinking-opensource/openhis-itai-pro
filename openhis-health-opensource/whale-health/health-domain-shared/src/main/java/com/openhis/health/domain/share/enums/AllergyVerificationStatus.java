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
 * 皮试验证状态（DM-CLN-001 §4.1.2 {@code verification_status_enum}）。
 */
@Getter
public enum AllergyVerificationStatus implements EnumInterface {

    /** 未确诊。 */
    UNCONFIRMED(0L, "UNCONFIRMED", "未确诊", "初步判断，尚未确认", 1L),
    /** 疑似。 */
    PROVISIONAL(0L, "PROVISIONAL", "疑似", "暂定状态，待进一步确认", 2L),
    /** 有差异。 */
    DIFFERENTIAL(0L, "DIFFERENTIAL", "有差异", "处于鉴别判断阶段", 3L),
    /** 已确诊。 */
    CONFIRMED(0L, "CONFIRMED", "已确诊", "已确认的诊断结论", 4L),
    /** 已排除。 */
    REFUTED(0L, "REFUTED", "已排除", "该项风险被排除", 5L),
    /** 录入错误。 */
    ENTERED_IN_ERROR(0L, "ENTERED_IN_ERROR", "未知", "原记录标记为录入错误", 6L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    AllergyVerificationStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }
}
