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
 * 发布状态枚举。
 */
@Getter
public enum PublicationStatus implements EnumInterface {
    /**
     * DRAFT。
     */
    DRAFT(0L, "DRAFT", "草稿", "", 1L),
    /**
     * ACTIVE。
     */
    ACTIVE(0L, "ACTIVE", "已启用", "", 2L),
    /**
     * INACTIVE。
     */
    INACTIVE(0L, "INACTIVE", "已停用", "", 3L),
    /**
     * DISCARDED。
     */
    DISCARDED(0L, "DISCARDED", "已废弃", "", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * PublicationStatus。
     */
    PublicationStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

}
