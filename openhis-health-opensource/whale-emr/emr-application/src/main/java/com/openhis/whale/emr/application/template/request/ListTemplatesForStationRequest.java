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
 * 医生站模板树：按场景与当前用户可见范围加载。
 */
@Getter
@Setter
public class ListTemplatesForStationRequest {

    /** OUTPATIENT | INPATIENT */
    private String stationKind;

    /** 文书类型编码 */
    private String documentTypeCode;

    /** 是否启用 */
    private Boolean enabled;
}
