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

package com.openhis.health.domain.administration.organization.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 输入码命令片段：拼音与五笔检索串，供机构/地点等主数据检索。
 */
@Getter
@Setter
/**
 * InputCodeCommand。
 */
public class InputCodeCommand {
    /** 拼音码。 */
    /**
     * pyStr。
     */
    private String pyStr;
    /** 五笔码。 */
    /**
     * wbStr。
     */
    private String wbStr;
}
