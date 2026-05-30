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

package com.openhis.health.domain.custom.ordertemplate.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.OrderTemplateLineKind;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 医嘱组套模板行（聚合内子实体）。
 */
@Getter
@Setter
@Entity
@Table(name = "cus_order_template_line")
public class OrderTemplateLine extends BaseAuditedEntity {

    @Column(name = "order_template_id", nullable = false)
    private Long orderTemplateId;

    @Column(name = "line_kind_enum", nullable = false)
    private String lineKindEnum;

    @Column(name = "medication_definition_id")
    private Long medicationDefinitionId;

    @Column(name = "activity_definition_id")
    private Long activityDefinitionId;

    @Column(name = "device_definition_id")
    private Long deviceDefinitionId;

    @Column(name = "group_key")
    private String groupKey;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "default_quantity")
    private BigDecimal defaultQuantity;

    @Column(name = "default_unit_code")
    private String defaultUnitCode;

    @Column(name = "default_days")
    private Integer defaultDays;

    @Column(name = "frequency_id")
    private Long frequencyId;

    @Column(name = "route_code")
    private String routeCode;

    @Column(name = "dosage_text")
    private String dosageText;

    @Column(name = "default_perform_org_id")
    private Long defaultPerformOrgId;

    @Column(name = "line_remark")
    private String lineRemark;

    /**
     * 断言行必填与互斥引用（BR-OTL-008/010/016）。
     */
    public void assertRequiredFields() {
        if (this.orderTemplateId == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "组套头标识不能为空");
        }
        if (this.lineKindEnum == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "行类型不能为空");
        }
        if (this.sortOrder == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "行内排序不能为空");
        }
        if (this.sortOrder <= 0) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "行内排序必须为正整数");
        }
        if (this.defaultQuantity != null && this.defaultQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "默认数量必须大于零");
        }
        this.assertKindAndDefinitionRefConsistent();
    }

    /**
     * 断言行类型与引用列互斥且一致（BR-OTL-008）。
     */
    public void assertKindAndDefinitionRefConsistent() {
        boolean medication = this.medicationDefinitionId != null;
        boolean service = this.activityDefinitionId != null;
        boolean device = this.deviceDefinitionId != null;

        if (OrderTemplateLineKind.MEDICATION.getCode().equals(this.lineKindEnum)) {
            if (!medication || service || device) {
                throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "药品行引用列不合法");
            }
            return;
        }
        if (OrderTemplateLineKind.SERVICE.getCode().equals(this.lineKindEnum)) {
            if (!service || medication || device) {
                throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "非药品行引用列不合法");
            }
            return;
        }
        if (OrderTemplateLineKind.DEVICE.getCode().equals(this.lineKindEnum)) {
            if (!device || medication || service) {
                throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "耗材行引用列不合法");
            }
            return;
        }
        throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "未知的行类型");
    }

    /**
      * 标记当前对象状态。
     */
    public void markDeleted() {
        this.setIsDeleted(Boolean.TRUE);
    }
}

