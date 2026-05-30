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

/* (C)2026 */
package com.openhis.health.domain.share.enums;

/** 枚举类通用接口 */
public interface EnumInterface {
    /**
     * 所属编码系统。
     */
    Long getCodeSystemId();

    /**
     * 编码（唯一标识，对应原来的 dictValue）。
     *
     * <p>例如：01, 02, A01.01。</p>
     */
    String getCode();

    /**
     * 显示名称（对应原来的 dictLabel）。
     *
     * <p>例如：中药饮片、中成药。</p>
     */
    String getDisplay();

    /**
     * 定义（详细描述，可选）。
     */
    String getDefinition();

    /**
     * 排序号（对应原来的 dictSort）。
     */
    Long getSortOrder();
}
