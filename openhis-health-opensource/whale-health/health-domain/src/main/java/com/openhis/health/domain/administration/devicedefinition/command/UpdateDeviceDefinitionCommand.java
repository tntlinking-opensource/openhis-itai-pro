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

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateDeviceDefinitionCommand extends BatchCommand<UpdateDeviceDefinitionCommand.Item> {
    /** 操作人标识。 */
    private Long operatorId;
    @Getter
    @Setter
    public static class Item {
        /** 器材定义标识。 */
        private Long id;
        /** 器材名称。 */
        private String name;
        /** 拼音检索码（{@code null} 表示不在本次请求中修改）。 */
        private String pyStr;
        /** 五笔检索码（{@code null} 表示不在本次请求中修改）。 */
        private String wbStr;
        /** 商品名称。 */
        private String merchandiseName;
        /** 分类编码。 */
        private String categoryCode;
        /** 种类编码。 */
        private String typeCode;
        /** 包装单位。 */
        private String unitCode;
        /** 包装规格描述。 */
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
        /** 产品型号。 */
        private String modelNumber;
        /** 高值器材标志（0/1）。 */
        private Integer hvcmFlag;
        /** 批准文号。 */
        private String approvalNumber;
        /** 所属科室标识。 */
        private Long orgId;
        /** 所在位置标识。 */
        private Long locationId;
        /** 医保编码。 */
        private String ybNo;
        /** 医药机构目录编码。 */
        private String ybOrgNo;
        /** 医保标记（0/1）。 */
        private Integer ybFlag;
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
        /** 编辑业务码（由他域判定）。 */
        private String editabilityCode;
        /** 变更字段摘要（供事件载荷使用）。 */
        private List<String> changedFields;
        /** 默认门诊/就诊单位编码。 */
        private String defEncounterUnitCode;
        /** 默认住院单位编码。 */
        private String defInhospitalUnitCode;
    }
}
