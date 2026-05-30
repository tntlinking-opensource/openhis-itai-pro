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
 * 就诊状态枚举。
 */
@Getter
public enum EncounterStatus implements EnumInterface {
    PLANNED(0L, "PLANNED", "待挂号", "", 1L),
    REGISTERED(0L, "REGISTERED", "已挂号", "", 2L),
    TRIAGED(0L, "TRIAGED", "已分诊", "", 3L),
    PRE_ADMISSION(0L, "PRE_ADMISSION", "预入院", "", 4L),
    IN_PROGRESS(0L, "IN_PROGRESS", "在诊", "", 5L),
    ON_HOLD(0L, "ON_HOLD", "暂离", "", 6L),
    DISCHARGED(0L, "DISCHARGED", "完诊", "", 7L),
    COMPLETED(0L, "COMPLETED", "已完成", "", 8L),
    CANCELLED(0L, "CANCELLED", "已取消", "", 9L),
    PENDING(0L, "PENDING", "待出院结算", "", 10L),
    ENTERED_IN_ERROR(0L, "ENTERED_IN_ERROR", "错误录入", "", 11L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    EncounterStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    /**
     * 判断是否为终止状态。
     *
     * @return 终止状态返回 true
     */
    public boolean isTerminal() {
        if (DISCHARGED.equals(this)) {
            return true;
        }
        if (CANCELLED.equals(this)) {
            return true;
        }
        if (ENTERED_IN_ERROR.equals(this)) {
            return true;
        }
        return false;
    }

}
