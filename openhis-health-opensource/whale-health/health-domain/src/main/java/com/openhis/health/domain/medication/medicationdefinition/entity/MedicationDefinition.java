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

package com.openhis.health.domain.medication.medicationdefinition.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药品定义聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "med_medication_definition")
public class MedicationDefinition extends BaseAuditedEntity {

    /** 药品编号（业务编码，机构内唯一）。 */
    @Column(name = "bus_no", nullable = false)
    private String busNo;
    /** 药品名称。 */
    @Column(name = "name", nullable = false)
    private String name;
    /** 适用范围编码。 */
    @Column(name = "domain_enum")
    private String domainEnum;
    /** 药品版本。 */
    @Column(name = "med_version")
    private String version;
    /** 英文药名。 */
    @Column(name = "name_en")
    private String nameEn;
    /** 药品名称拼音码。 */
    @Column(name = "py_str")
    private String pyStr;
    /** 药品名称五笔码。 */
    @Column(name = "wb_str")
    private String wbStr;
    /** 药品分类编码。 */
    @Column(name = "category_code")
    private String categoryCode;
    /** 商品名称。 */
    @Column(name = "merchandise_name")
    private String merchandiseName;
    /** 商品名拼音码。 */
    @Column(name = "merchandise_py_str")
    private String merchandisePyStr;
    /** 商品名五笔码。 */
    @Column(name = "merchandise_wb_str")
    private String merchandiseWbStr;
    /** 采购单位（5类单位之一）。 */
    @Column(name = "purchase_unit_code")
    private String purchaseUnitCode;
    /** 拆零单位（5类单位之一）。 */
    @Column(name = "split_unit_code")
    private String splitUnitCode;
    /** 所含耗材描述。 */
    @Column(name = "comprised_text")
    private String comprisedText;
    /** 成分。 */
    @Column(name = "ingredient")
    private String ingredient;
    /** 剂量形式编码。 */
    @Column(name = "dose_from")
    private Integer doseFrom;
    /** 批准文号。 */
    @Column(name = "approval_number")
    private String approvalNumber;
    /** 医保是否已对码。 */
    @Column(name = "yb_match_flag")
    private Boolean ybMatchFlag;
    /** 医保编码。 */
    @Column(name = "yb_no")
    private String ybNo;
    /** 药理作用分类编码。 */
    @Column(name = "pharmacology_category_code")
    private String pharmacologyCategoryCode;
    /** 是否皮试药。 */
    @Column(name = "skin_test_flag")
    private Boolean skinTestFlag;
    /** 是否注射药物。 */
    @Column(name = "inject_flag")
    private Boolean injectFlag;
    /** 生产厂家标识。 */
    @Column(name = "manufacturer_id")
    private Long manufacturerId;
    /** 生产厂商文本。 */
    @Column(name = "manufacturer_text")
    private String manufacturerText;
    /** 供应商标识。 */
    @Column(name = "supply_id")
    private Long supplyId;
    /** 是否限制使用。 */
    @Column(name = "restricted_flag")
    private Boolean restrictedFlag;
    /** 限制使用范围。 */
    @Column(name = "restricted_scope")
    private String restrictedScope;
    /** 启用标记（true=启用，false=停用）。 */
    @Column(name = "active_flag")
    private Boolean activeFlag;
    /** 儿童用药标志。 */
    @Column(name = "children_flag")
    private Boolean childrenFlag;
    /** 产品特性编码。 */
    @Column(name = "characteristic")
    private Integer characteristic;
    /** 最小库存警戒数量。 */
    @Column(name = "item_min_quantity")
    private BigDecimal itemMinQuantity;
    /** 最大库存警戒数量。 */
    @Column(name = "item_max_quantity")
    private BigDecimal itemMaxQuantity;
    /** 默认门诊单位编码。 */
    @Column(name = "def_encounter_unit_code")
    private String defEncounterUnitCode;
    /** 默认住院单位编码。 */
    @Column(name = "def_inhospital_unit_code")
    private String defInhospitalUnitCode;
    /** 国家药品贯标编码。 */
    @Column(name = "national_drug_code")
    private String nationalDrugCode;
    /** 拆分属性编码。 */
    @Column(name = "part_attribute_enum")
    private String partAttributeEnum;
    /** 抗生素分类编码。 */
    @Column(name = "antibiotic_code")
    private String antibioticCode;
    /** 权限限制枚举编码。 */
    @Column(name = "restricted_enum")
    private String restrictedEnum;
    /** 是否自制。 */
    @Column(name = "self_flag")
    private Boolean selfFlag;
    /** 是否抗生素。 */
    @Column(name = "antibiotic_flag")
    private Boolean antibioticFlag;
    /** 是否基药。 */
    @Column(name = "basic_flag")
    private Boolean basicFlag;
    /** 住院临时医嘱拆分属性编码。 */
    @Column(name = "tho_part_attribute_enum")
    private String thoPartAttributeEnum;
    /** 医保等级。 */
    @Column(name = "chrgitm_lv")
    private Integer chrgitmLv;
    /** 处方药标志。 */
    @Column(name = "rx_flag")
    private Boolean rxFlag;
    /** 用药说明。 */
    @Column(name = "dosage_instruction")
    private String dosageInstruction;
    /** 药品 69 码。 */
    @Column(name = "drug69_code")
    private String drug69Code;
    /** 规格。 */
    @Column(name = "total_volume")
    private String totalVolume;
    /** 剂型编码。 */
    @Column(name = "dose_form_code")
    private String doseFormCode;
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

    /** 库存单位（5类单位之一）。 */
    @Column(name = "stock_unit_code")
    private String stockUnitCode;

    /** 销售单位（5类单位之一）。 */
    @Column(name = "package_unit_code")
    private String packageUnitCode;
    /** 默认用法编码。 */
    @Column(name = "method_code")
    private String methodCode;
    /** 默认频次编码。 */
    @Column(name = "rate_code")
    private String rateCode;
    /** 单次剂量。 */
    @Column(name = "dose")
    private BigDecimal dose;
    /** 单次最大剂量。 */
    @Column(name = "max_unit")
    private BigDecimal maxUnit;
    /** 用量限定。 */
    @Column(name = "usage_limit")
    private BigDecimal usageLimit;
    /** 采购入库位置（他域事实列）。 */
    @Column(name = "location_id")
    private Long locationId;
    /** 生命周期状态编码（DRAFT/ACTIVE/INACTIVE/DISCARDED）。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;
    /** 机构标识。 */
    @Column(name = "org_id", nullable = false)
    private Long orgId;
    /** 成分项。 */
    @Column(name = "ingredient_item")
    private String ingredientItem;
    /** 药品说明。 */
    @Column(name = "medicationdefinition")
    private String definition;
    /** DDD（日限定剂量）编码。 */
    @Column(name = "ddd_code")
    private String dddCode;
    /** DDD（日限定剂量）单位编码。 */
    @Column(name = "ddd_unit_code")
    private String dddUnitCode;
    /** 最小频次编码。 */
    @Column(name = "min_rate_code")
    private String minRateCode;
    /** 最大频次编码。 */
    @Column(name = "max_rate_code")
    private String maxRateCode;

    /**
     * 初始化为已发布。
     */
    public void initPublished() {
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
        this.activeFlag = Boolean.TRUE;
    }

    /**
     * 断言创建必填。
     */
    public void validateRequiredForCreate() {
        if (StringUtils.isBlank(this.name)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DEFINITION_INVALID_ARGUMENT, "药品名称不能为空");
        }
        if (this.orgId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DEFINITION_INVALID_ARGUMENT, "机构标识不能为空");
        }
    }

    /**
     * 断言允许编辑。
     */
    public void ensureEditableStatus() {
        if (PublicationStatus.DISCARDED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DEFINITION_INVALID_STATUS, "已废弃目录不可编辑");
        }
    }

    /**
     * 断言可停用。
     */
    public void ensureRetirable() {
        if (!PublicationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DEFINITION_INVALID_STATUS, "仅已发布状态允许停用");
        }
    }

    /**
     * 断言可重新启用。
     */
    public void ensureActivatable() {
        if (!PublicationStatus.INACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DEFINITION_INVALID_STATUS, "仅停用状态允许重新启用");
        }
    }

    /**
     * 迁移为停用。
     */
    public void retire() {
        this.statusEnum = PublicationStatus.INACTIVE.getCode();
        this.activeFlag = Boolean.FALSE;
    }

    /**
     * 迁移为启用。
     */
    public void activate() {
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
        this.activeFlag = Boolean.TRUE;
    }

    /**
     * 逻辑废弃。
     *
     * @param operatorId 操作人
     */
    public void markDeleted(Long operatorId) {
        this.statusEnum = PublicationStatus.DISCARDED.getCode();
        this.activeFlag = Boolean.FALSE;
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }

    /**
     * 校验医保对码参数。
     */
    public void validateInsuranceMapping() {
        if (Boolean.TRUE.equals(this.ybMatchFlag) && StringUtils.isBlank(this.ybNo)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DEFINITION_INVALID_ARGUMENT, "医保对码时医保编码不能为空");
        }
    }
}

