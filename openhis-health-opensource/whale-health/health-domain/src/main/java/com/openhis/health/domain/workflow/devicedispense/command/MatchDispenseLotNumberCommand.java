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

package com.openhis.health.domain.workflow.devicedispense.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 批号匹配（待发门控）命令。
 */
@Getter
@Setter
/**
 * MatchDispenseLotNumberCommand。
 */
public class MatchDispenseLotNumberCommand {

    /** 标识列表或请求key。 */
    /**
     * idListOrRequestKey。
     */
    private String idListOrRequestKey;

    /** 操作人主键。 */
    /**
     * operatorId。
     */
    private Long operatorId;
}
