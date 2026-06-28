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
 * 打印客户端类型（DM-PRT-002 §9.2 · {@code prt_print_event_log.client_type}）。
 */
@Getter
public enum PrintClientType implements EnumInterface {

    /** Vue + OpenHIS.Win 路径默认客户端。 */
    OPENHIS_WIN(0L, "openhis-win", "OpenHIS Windows 客户端", "", 1L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    PrintClientType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    /**
     * 判断客户端类型码是否与当前枚举值匹配。
     *
     * @param clientTypeCode 库表 {@code client_type} 列值
     * @return 匹配返回 {@code true}
     */
    public boolean sameAs(String clientTypeCode) {
        return this.code.equals(clientTypeCode);
    }
}
