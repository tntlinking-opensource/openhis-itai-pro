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

package com.openhis.health.domain.workflow.activitydefinition.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量导入诊疗目录结果摘要。
 *
 * <p>该结果仅用于上层应用服务或接口层进行导入反馈展示；不参与领域状态计算。</p>
 */
@Getter
@Setter
public class ImportActivityCatalogResult {

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
         * 构造单行导入失败明细。
         *
         * @param lineNo 失败行号（从 1 起）
         * @param message 失败原因描述
         */
        public ImportFailure(Integer lineNo, String message) {
            this.lineNo = lineNo;
            this.message = message;
        }
    }
}

