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

package com.openhis.health.application.basic.executelocation.request;

import lombok.Data;

/**
 * 删除执行规则时的主键载体：以字符串传递 Long，避免部分客户端 JSON 精度问题。
 */
@Data
/**
 * ExecuteLocationIdValueRequest。
 */
public class ExecuteLocationIdValueRequest {

    /** 规则主键的十进制字符串。 */
    /**
     * value。
     */
    private String value;
}
