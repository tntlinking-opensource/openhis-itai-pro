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

package com.openhis.health.domain.administration.devicedefinition.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;

/**
 * 器材定义聚合根（DM-ADM-008 §3.1，SDD §4.2）。
 *
 * <p>职责：维护器材/耗材目录主数据，承载业务编码、名称、分类、包装、医保字段等，供下游业务引用。</p>
 *
 * <p>注意：本实体字段按 DM/SDD 定义为主真源；若出现存量字段与文档不一致，需在字段 JavaDoc 留痕
 * 并在代码中收敛为可编译且可运行的兼容口径。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "adm_device_definition")
public class DeviceDefinition extends BaseAuditedEntity {
    /** 器材名称。 */
    @Column(name = "name", nullable = false)
    private String name;
    /** 商品名称。 */
    @Column(name = "merchandise_name")
    private String merchandiseName;
    /** 商品名拼音检索码。 */
    @Column(name = "merchandise_py_str")
    private String merchandisePyStr;
    /** 商品名五笔检索码。 */
    @Column(name = "merchandise_wb_str")
    private String merchandiseWbStr;
    /** 器材业务编码（院内唯一）。 */
    @Column(name = "bus_no")
    private String busNo;
    /** 拼音码（用于检索）。 */
    @Column(name = "py_str")
    private String pyStr;
    /** 五笔码（用于检索）。 */
    @Column(name = "wb_str")
    private String wbStr;
    /** 器材分类编码。 */
    @Column(name = "category_code")
    private String categoryCode;
    /** 器材种类编码。 */
    @Column(name = "type_code")
    private String typeCode;
    /** 采购单位（5类单位之一）。 */
    @Column(name = "purchase_unit_code")
    private String purchaseUnitCode;
    /** 包装规格描述（与药品 total_volume 语义对齐）。 */
    @Column(name = "total_volume")
    private String totalVolume;
    /** 最小库存警戒量。 */
    @Column(name = "item_min_quantity")
    private BigDecimal itemMinQuantity;
    /** 最大库存警戒量。 */
    @Column(name = "item_max_quantity")
    private BigDecimal itemMaxQuantity;
    /** 拆零单位（5类单位之一）。 */
    @Column(name = "split_unit_code")
    private String splitUnitCode;
    /** 所属科室标识。 */
    @Column(name = "org_id")
    private Long orgId;
    /** 所在位置标识。 */
    @Column(name = "location_id")
    private Long locationId;
    /** 默认门诊/就诊单位编码。 */
    @Column(name = "def_encounter_unit_code")
    private String defEncounterUnitCode;
    /** 默认住院单位编码。 */
    @Column(name = "def_inhospital_unit_code")
    private String defInhospitalUnitCode;
    /** 产品型号。 */
    @Column(name = "model_number")
    private String modelNumber;
    /** 高值器材标志。 */
    @Column(name = "hvcm_flag")
    private Boolean hvcmFlag;
    /** 销售单位（5类单位之一）。 */
    @Column(name = "package_unit_code")
    private String packageUnitCode;

    /** 库存单位（5类单位之一）。 */
    @Column(name = "stock_unit_code")
    private String stockUnitCode;

    /** 剂量单位（5类单位之一）。 */
    @Column(name = "dose_unit_code")
    private String doseUnitCode;

    /** 剂量单位→拆零单位换算倍数（拆零单位为1）。 */
    @Column(name = "dose_unit_ratio")
    private BigDecimal doseUnitRatio;

    /** 采购单位→拆零单位换算倍数（拆零单位为1）。 */
    @Column(name = "purchase_unit_ratio")
    private BigDecimal purchaseUnitRatio;

    /** 库存单位→拆零单位换算倍数（拆零单位为1）。 */
    @Column(name = "stock_unit_ratio")
    private BigDecimal stockUnitRatio;

    /** 销售单位→拆零单位换算倍数（拆零单位为1）。 */
    @Column(name = "package_unit_ratio")
    private BigDecimal packageUnitRatio;

    /** 批准文号。 */
    @Column(name = "approval_number")
    private String approvalNumber;
    /** 生产厂家标识。 */
    @Column(name = "manufacturer_id")
    private Long manufacturerId;
    /** 生产厂家名称。 */
    @Column(name = "manufacturer_text")
    private String manufacturerText;
    /** 供应商标识。 */
    @Column(name = "supply_id")
    private Long supplyId;
    /** 器材说明。 */
    @Column(name = "description")
    private String description;
    /** 适用范围描述。 */
    @Column(name = "jurisdiction")
    private String jurisdiction;
    /** 器材版本。 */
    @Column(name = "device_version")
    private String version;
    /** 主要成分描述。 */
    @Column(name = "substance_text")
    private String substanceText;
    /** 过敏标记。 */
    @Column(name = "allergen_flag")
    private Boolean allergenFlag;
    /** 处方标志。 */
    @Column(name = "rx_flag")
    private Boolean rxFlag;
    /** 医保标记。 */
    @Column(name = "yb_flag")
    private Boolean ybFlag;
    /** 医保编码。 */
    @Column(name = "yb_no")
    private String ybNo;
    /** 医药机构目录编码。 */
    @Column(name = "yb_org_no")
    private String ybOrgNo;
    /** 医保对码标记。 */
    @Column(name = "yb_match_flag")
    private Boolean ybMatchFlag;
    /** 医保等级。 */
    @Column(name = "chrgitm_lv")
    private Integer chrgitmLv;

    /** 发布状态（存储为整数码）。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;
    /**
     * 是否启用标记。
     *
     * <p>DOC-CONFLICT：DM/SDD 未定义 activeFlag 字段；存量表与代码存在该列，当前保留以兼容既有查询/前端展示口径。</p>
     */
    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag;

    /**
     * 医保对码标识。
     *
     * <p>DOC-CONFLICT：DM/SDD 定义的医保回写字段为 ybFlag/ybNo/ybOrgNo/ybMatchFlag/chrgitmLv；
     * 存量实现使用 ybMappingCode。当前保留该列以兼容存量调用方，但回写方法优先按 SDD 字段全集落地。</p>
     */
    @Column(name = "yb_mapping_code")
    private String ybMappingCode;

    /**
     * 创建前断言（SDD §5.1.1，BR-DEVDEF-001）。
     *
     * @throws BusinessException 参数非法
     */
    public void assertCreatable() {
        if (StringUtils.isBlank(this.busNo)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_INVALID_ARGUMENT, "器材业务编码不能为空");
        }
        if (StringUtils.isBlank(this.name)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_INVALID_ARGUMENT, "器材名称不能为空");
        }
    }

    /**
     * 初始化为启用状态（SDD §5.1.1）。
     */
    public void initAsActive() {
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
        this.activeFlag = Boolean.TRUE;
    }

    /**
     * 断言当前记录允许按业务码更新（SDD §5.1.2，BR-DEVDEF-005/BR-DEVDEF-007）。
     *
     * <p>说明：业务码的计算来自他域，本聚合只按入参 editabilityCode 做门控。</p>
     *
     * @param editabilityCode 可编辑业务码
     * @throws BusinessException 规则冲突
     */
    public void assertUpdatableByBusinessCode(String editabilityCode) {
        if (StringUtils.isBlank(editabilityCode)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_INVALID_ARGUMENT, "编辑业务码不能为空");
        }
        if (!"OK".equalsIgnoreCase(editabilityCode.trim())) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_RULE_CONFLICT, "当前器材目录不允许编辑，业务码=" + editabilityCode);
        }
    }

    /**
     * 名称变更后重算检索码（BR-DEVDEF-002）。
     */
    public void refreshSearchCodeOnNameChanged() {
        if (this.name == null) {
            this.pyStr = "";
            this.wbStr = "";
            return;
        }
        this.pyStr = SearchCodeUtil.toPyStr(this.name);
        this.wbStr = SearchCodeUtil.toWbStr(this.name);
    }

    /**
     * 按商品名称重算商品拼音码与五笔码。
     */
    public void refreshMerchandiseSearchCodes() {
        if (StringUtils.isBlank(this.merchandiseName)) {
            this.merchandisePyStr = null;
            this.merchandiseWbStr = null;
            return;
        }
        this.merchandisePyStr = SearchCodeUtil.toPyStr(this.merchandiseName);
        this.merchandiseWbStr = SearchCodeUtil.toWbStr(this.merchandiseName);
    }

    /**
     * 断言允许停用（SDD §5.1.3）。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertRetirable() {
        if (!PublicationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_INVALID_STATUS, "仅启用状态的器材目录允许停用");
        }
    }

    /**
     * 迁移到停用（ACTIVE -> INACTIVE）（SDD §5.1.3）。
     *
     * @throws BusinessException 状态不允许
     */
    public void transitToInactive() {
        this.assertRetirable();
        this.statusEnum = PublicationStatus.INACTIVE.getCode();
        this.activeFlag = Boolean.FALSE;
    }

    /**
     * 断言允许启用（SDD §5.1.4）。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertActivatable() {
        if (!PublicationStatus.INACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_INVALID_STATUS, "仅停用状态的器材目录允许启用");
        }
    }

    /**
     * 迁移到启用（INACTIVE -> ACTIVE）（SDD §5.1.4）。
     *
     * @throws BusinessException 状态不允许
     */
    public void transitToActive() {
        this.assertActivatable();
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
        this.activeFlag = Boolean.TRUE;
    }

    /**
     * 导入行断言（SDD §5.1.5，BR-DEVDEF-001）。
     *
     * @throws BusinessException 参数非法
     */
    public void assertImportItemValid() {
        if (StringUtils.isBlank(this.busNo)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_INVALID_ARGUMENT, "导入器材目录时 busNo 不能为空");
        }
        if (StringUtils.isBlank(this.name)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_INVALID_ARGUMENT, "导入器材目录时 name 不能为空");
        }
    }

    /**
     * 医保回写载荷断言（SDD §5.1.6）。
     *
     * @throws BusinessException 参数非法
     */
    public void assertYbMappingPayloadValid() {
        // SDD 字段表允许为空的字段不强制必填；但若携带 ybNo/ybOrgNo 则需去空格归一化由调用方完成。
        if (this.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_INVALID_ARGUMENT, "器材定义标识不能为空");
        }
    }
}
