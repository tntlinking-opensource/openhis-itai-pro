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
 * 号源日程业务状态（DM-WKF-009 §4.2）。
 */
@Getter
public enum ScheduleStatus implements EnumInterface {
    /**
     * DRAFT。
     */
    DRAFT(0L, "draft", "草稿", "", 1L),
    /**
     * ACTIVE。
     */
    ACTIVE(0L, "active", "已启用", "", 2L),
    /**
     * INACTIVE。
     */
    INACTIVE(0L, "inactive", "已停用", "", 3L),
    /**
     * RETIRED。
     */
    RETIRED(0L, "retired", "已废弃", "", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * ScheduleStatus。
     */
    ScheduleStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

}
