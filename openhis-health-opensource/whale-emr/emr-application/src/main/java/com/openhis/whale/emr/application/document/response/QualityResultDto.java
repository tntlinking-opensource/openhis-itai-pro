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

package com.openhis.whale.emr.application.document.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class QualityResultDto {

    /** passed（业务字段） */
    private boolean passed;
    /** 质控问题列表（预留） */
    private List<QualityIssueDto> issues;

    @Getter
    @Setter
    public static class QualityIssueDto {
        private String level;        // INFO / WARN / ERROR
        private String dataItemName; // 关联的数据项名（可选）
        private String location;     // 位置描述（区域/数据组/数据元）
        private String message;      // 问题描述
    }
}

