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

package com.openhis.health.application.basic.servicedevicebinding.request;

import lombok.Data;

/**
 * 诊疗耗材绑定页：按关键字检索可选耗材定义下拉。
 */
@Data
public class ListDeviceDefOptionsRequest {

    /** 综合关键字（编码/名称/拼音/五笔）；空白则返回全部启用耗材。 */
    private String keyword;
}
