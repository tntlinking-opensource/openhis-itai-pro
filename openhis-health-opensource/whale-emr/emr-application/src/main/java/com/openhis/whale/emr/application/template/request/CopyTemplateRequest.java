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

package com.openhis.whale.emr.application.template.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 复制模板请求（新名称、新编码，其余可继承）
 */
@Getter
@Setter
public class CopyTemplateRequest {

    /** 创建文书时使用的源模板 id */
    private String sourceTemplateId;
    /** 副本新名称 */
    private String newName;
    /** 副本新编码 */
    private String newCode;
}
