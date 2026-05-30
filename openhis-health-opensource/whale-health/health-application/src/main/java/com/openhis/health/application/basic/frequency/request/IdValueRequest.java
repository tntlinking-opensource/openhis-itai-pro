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

package com.openhis.health.application.basic.frequency.request;

import lombok.Data;

/**
 * 通用字符串主键请求壳：用于启用/停用等接口接收 Long 型 id，避免 JSON 长整型精度问题。
 */
@Data
/**
 * IdValueRequest。
 */
public class IdValueRequest {

    /** 目标主键的十进制字符串；应用层 trim 后解析为 {@link Long}。 */
    /**
     * value。
     */
    private String value;
}
