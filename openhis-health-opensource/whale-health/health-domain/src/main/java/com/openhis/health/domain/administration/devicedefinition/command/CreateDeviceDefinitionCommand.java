/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.devicedefinition.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 批量创建器材定义命令（DM-ADM-008 §6.1）。
 *
 * <p>外层 {@link #operatorId} 为审计操作人；明细在 {@link Item} 中携带主数据字段。</p>
 */
@Getter
@Setter
public class CreateDeviceDefinitionCommand extends BatchCommand<CreateDeviceDefinitionCommand.Item> {
    /** 操作人标识。 */
    private Long operatorId;
    @Getter
    @Setter
    public static class Item {
        /** 器材业务编码。 */
        private String busNo;
        /** 器材名称。 */
        private String name;
        /** 拼音检索码（可选）。 */
        private String pyStr;
        /** 五笔检索码（可选）。 */
        private String wbStr;
        /** 商品名称。 */
        private String merchandiseName;
        /** 器材分类编码。 */
        private String categoryCode;
        /** 器材种类编码。 */
        private String typeCode;
        /** 包装单位。 */
        private String unitCode;
        /** 包装规格描述（与药品 total_volume 对齐）。 */
        private String totalVolume;
        /** 最小使用单位。 */
        private String minUnitCode;
        /** 销售单位。 */
        private String packageUnitCode;
        /** 库存单位。 */
        private String stockUnitCode;
        /** 剂量单位。 */
        private String doseUnitCode;
        /** 剂量单位→拆零单位换算倍数。 */
        private java.math.BigDecimal doseUnitRatio;
        /** 采购单位→拆零单位换算倍数。 */
        private java.math.BigDecimal purchaseUnitRatio;
        /** 库存单位→拆零单位换算倍数。 */
        private java.math.BigDecimal stockUnitRatio;
        /** 销售单位→拆零单位换算倍数。 */
        private java.math.BigDecimal packageUnitRatio;
        /**
         * 追溯码贴码单位（可空；有码时须与 ratio 同时维护）。
         */
        private String traceUnitCode;
        /**
         * 1 个追溯码单位对应的拆零数量；可空；有值时须 &gt; 0。
         */
        private java.math.BigDecimal traceUnitRatio;
        /** 所属科室标识。 */
        private Long orgId;
        /** 所在位置标识。 */
        private Long locationId;
        /** 产品型号。 */
        private String modelNumber;
        /** 高值器材标志（0/1）。 */
        private Integer hvcmFlag;
        /** 收费耗材标记（0/1；1=收费；界面「不收费耗材」勾选对应 0）。 */
        private Integer chargeableFlag;
        /** 病案结算类型编码。 */
        private String mrSettlementTypeCode;
        /** 批准文号。 */
        private String approvalNumber;
        /** 医保标记（0/1）。 */
        private Integer ybFlag;
        /** 医保编码。 */
        private String ybNo;
        /** 医保对码标记（0/1）。 */
        private Integer ybMatchFlag;
        /** 医保等级。 */
        private Integer chrgitmLv;
        /** 主要成分描述。 */
        private String substanceText;
        /** 生产厂家标识。 */
        private Long manufacturerId;
        /** 生产厂家名称。 */
        private String manufacturerText;
        /** 供应商标识。 */
        private Long supplyId;
        /** 过敏标记（0/1）。 */
        private Integer allergenFlag;
        /** 适用范围描述。 */
        private String jurisdiction;
        /** 最小库存警戒量。 */
        private java.math.BigDecimal itemMinQuantity;
        /** 最大库存警戒量。 */
        private java.math.BigDecimal itemMaxQuantity;
        /** 器材说明。 */
        private String description;
        /** 默认门诊/就诊单位编码。 */
        private String defEncounterUnitCode;
        /** 默认住院单位编码。 */
        private String defInhospitalUnitCode;
    }
}
