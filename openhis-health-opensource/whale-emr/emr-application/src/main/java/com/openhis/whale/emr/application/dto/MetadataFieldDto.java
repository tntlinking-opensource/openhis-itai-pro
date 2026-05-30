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

package com.openhis.whale.emr.application.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class MetadataFieldDto {

    private Long id;
    private String fieldKey;
    private String label;
    private String dataType;
    private Boolean editableDefault;
    private String valueSource;
    private String sourcePath;
    private Map<String, Object> sourceConfig;
    private String defaultValue;
    private String maskRule;
    private String formatPattern;
    private Integer sortOrder;
    private Boolean isEnabled;
    private String remark;
}
