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

package com.openhis.whale.emr.application.documentType.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDocumentTypeRequest {

    /** 主键 id */
    private Long id;
    /** 名称 */
    private String name;
    /** 默认模板 id */
    private Long defaultTemplateId;
    /** 为 true 时清空默认模板，优先级高于 defaultTemplateId */
    private Boolean clearDefaultTemplate;
    /** 是否启用 */
    private Boolean isEnabled;
    /** 排序序号 */
    private Integer sortOrder;
}
