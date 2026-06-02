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

package com.openhis.health.domain.administration.devicedefinition.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 器材目录导入结果摘要（DM-ADM-008 §6.1.5，SDD §5.1.5）。
 *
 * <p>该结果用于上层应用服务或接口层进行导入反馈展示；不参与领域状态计算。</p>
 */
@Getter
@Setter
public class ImportDeviceDefinitionCatalogResult {

    /** 新增条数。 */
    private Integer createdCount;

    /** 更新条数。 */
    private Integer updatedCount;

    /** 失败条数。 */
    private Long failureCount;

    /** 失败明细。 */
    private List<ImportFailure> failures;

    /**
     * 单行失败明细。
     */
    @Getter
    @Setter
    public static class ImportFailure {
        /** 失败行号（从 1 开始）。 */
        private Integer lineNo;

        /** 失败原因。 */
        private String message;

        /**
         * ImportFailure。
         */
        public ImportFailure() {
        }

        /**
         * ImportFailure。
         *
         * @param lineNo 行号（从 1 开始）
         * @param message 失败原因
         */
        public ImportFailure(Integer lineNo, String message) {
            this.lineNo = lineNo;
            this.message = message;
        }
    }
}

