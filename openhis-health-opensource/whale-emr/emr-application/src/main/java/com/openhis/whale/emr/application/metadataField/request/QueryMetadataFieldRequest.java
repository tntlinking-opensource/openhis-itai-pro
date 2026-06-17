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

package com.openhis.whale.emr.application.metadataField.request;

import com.openhis.whale.core.application.dto.BaseQueryRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryMetadataFieldRequest extends BaseQueryRequest {

    /** 关键字（匹配名称/编码等） */
    private String keyword;
    /** 取值来源枚举 code（CONST/SYSTEM/PATIENT 等） */
    private String valueSource;
    /** 是否启用 */
    private Boolean isEnabled;
}
