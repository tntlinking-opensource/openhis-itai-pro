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
 * 药品申请状态枚举。
 */
@Getter
/**
 * RequestStatus。
 */
public enum RequestStatus implements EnumInterface {
    /**
     * DRAFT。
     */
    DRAFT(0L, "DRAFT", "待签发", "", 1L),
    /**
     * ACTIVE。
     */
    ACTIVE(0L, "ACTIVE", "已发送", "", 2L),
    /**
     * VERIFIED。
     */
    VERIFIED(0L, "VERIFIED", "已校对", "", 3L),
    /**
     * HOLD。
     */
    HOLD(0L, "HOLD", "暂挂", "", 4L),
    /**
     * CANCELLED。
     */
    CANCELLED(0L, "CANCELLED", "已取消", "", 5L),
    /**
     * STOPPED。
     */
    STOPPED(0L, "STOPPED", "已停嘱", "", 6L),
    /**
     * COMPLETED。
     */
    COMPLETED(0L, "COMPLETED", "已完成", "", 7L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * RequestStatus。
     */
    RequestStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

}
