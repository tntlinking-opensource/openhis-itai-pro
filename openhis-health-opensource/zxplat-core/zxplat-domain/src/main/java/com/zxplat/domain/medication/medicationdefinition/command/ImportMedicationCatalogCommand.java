/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdefinition.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量导入药品目录命令。
 */
@Getter
@Setter
public class ImportMedicationCatalogCommand {
    /** 导入批次号。 */
    private String batchNo;
    /** 操作人标识。 */
    private Long operatorId;
    /** 文件标识。 */
    private String fileId;
    /** 导入行集合。 */
    private List<MedicationDefinitionImportRowCommand> rows;

    /**
     * 导入行命令。
     */
    @Getter
    @Setter
    public static class MedicationDefinitionImportRowCommand {
        /** 业务编码。 */
        private String busNo;
        /** 药品名称。 */
        private String name;
        /** 药品分类编码。 */
        private String categoryCode;
        /** 采购单位编码。 */
        private String purchaseUnitCode;
        /** 机构标识。 */
        private Long orgId;
    }
}
