/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
