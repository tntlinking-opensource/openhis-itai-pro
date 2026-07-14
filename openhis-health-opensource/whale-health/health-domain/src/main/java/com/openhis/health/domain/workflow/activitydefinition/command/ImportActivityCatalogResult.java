/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
