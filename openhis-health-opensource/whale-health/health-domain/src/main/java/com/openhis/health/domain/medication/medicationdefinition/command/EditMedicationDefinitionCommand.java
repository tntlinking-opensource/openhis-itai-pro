/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdefinition.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 编辑药品定义命令。
 */
@Getter
@Setter
public class EditMedicationDefinitionCommand
    extends BatchCommand<EditMedicationDefinitionCommand.EditMedicationDefinitionItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 编辑药品定义子命令。
     */
    @Getter
    @Setter
    public static class EditMedicationDefinitionItemCommand {
        /** 药品定义标识。 */
        private Long id;
        /** 药品编码。 */
        private String busNo;
        /** 药品名称。 */
        private String name;
        /** 商品名称。 */
        private String merchandiseName;
        /** 药品拼音码。 */
        private String pyStr;
        /** 药品五笔码。 */
        private String wbStr;
        /** 商品拼音码。 */
        private String merchandisePyStr;
        /** 商品五笔码。 */
        private String merchandiseWbStr;
        /** 药品分类编码。 */
        private String categoryCode;
        /** 采购单位编码。 */
        private String purchaseUnitCode;
        /** 拆零单位编码。 */
        private String splitUnitCode;
        /** 销售单位编码。 */
        private String packageUnitCode;
        /** 库存单位编码。 */
        private String stockUnitCode;
        /** 剂量单位编码。 */
        private String doseUnitCode;
        /** 剂量单位 -> 拆零单位换算倍数。 */
        private java.math.BigDecimal doseUnitRatio;
        /** 采购单位 -> 拆零单位换算倍数。 */
        private java.math.BigDecimal purchaseUnitRatio;
        /** 库存单位 -> 拆零单位换算倍数。 */
        private java.math.BigDecimal stockUnitRatio;
        /** 销售单位 -> 拆零单位换算倍数。 */
        private java.math.BigDecimal packageUnitRatio;
        /** 默认库房。 */
        private Long locationId;
        /** 剂型编码。 */
        private String doseFormCode;
        /** 用法编码。 */
        private String methodCode;
        /** 频次编码。 */
        private String rateCode;
        /** 用药说明。 */
        private String dosageInstruction;
        /** 医保编码。 */
        private String ybNo;
        /** 医保对码。 */
        private Boolean ybMatchFlag;
        /** 医保等级。 */
        private Integer chrgitmLv;
        /** 病案结算类型编码。 */
        private String mrSettlementTypeCode;
        /** 药理分类。 */
        private String pharmacologyCategoryCode;
        /** 生产厂家ID。 */
        private Long manufacturerId;
        /** 生产厂家文本。 */
        private String manufacturerText;
        /** 供应商ID。 */
        private Long supplyId;
        /** 批准文号。 */
        private String approvalNumber;
        /** 贯标国家码。 */
        private String nationalDrugCode;
        /** 规格。 */
        private String totalVolume;
        /** 用量限定。 */
        private java.math.BigDecimal usageLimit;
        /** 单次剂量。 */
        private java.math.BigDecimal dose;
        /** 单次最大剂量。 */
        private java.math.BigDecimal maxUnit;
        /** 限制范围。 */
        private String restrictedScope;
        /** 门诊拆分属性。 */
        private String partAttributeEnum;
        /** 住院临时医嘱拆分属性。 */
        private String thoPartAttributeEnum;
        /** 最小库存。 */
        private java.math.BigDecimal itemMinQuantity;
        /** 最大库存。 */
        private java.math.BigDecimal itemMaxQuantity;
        /** 业务标识：是否基药。 */
        private Boolean basicFlag;
        /** 业务标识：是否需皮试。 */
        private Boolean skinTestFlag;
        /** 业务标识：是否注射剂。 */
        private Boolean injectFlag;
        /** 业务标识：是否儿童专用。 */
        private Boolean childrenFlag;
        /** 业务标识：是否自备药。 */
        private Boolean selfFlag;
        /** 业务标识：是否启用。 */
        private Boolean activeFlag;
        /** 业务标识：是否限制使用。 */
        private Boolean restrictedFlag;
        /** 业务标识：是否抗生素。 */
        private Boolean antibioticFlag;
        /** 业务标识：是否处方药。 */
        private Boolean rxFlag;
        /** 药品69码。 */
        private String drug69Code;
        /** 药品说明。 */
        private String definition;
        /** DDD编码。 */
        private String dddCode;
        /** DDD单位。 */
        private String dddUnitCode;
        /** 最小频次。 */
        private String minRateCode;
        /** 最大频次。 */
        private String maxRateCode;
        /** 关联门诊单位编码。 */
        private String defEncounterUnitCode;
        /** 关联住院单位编码。 */
        private String defInhospitalUnitCode;
    }
}
