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
import com.openhis.health.domain.share.converter.OrderTemplateSceneConverter;
import com.openhis.health.domain.share.enums.OrderTemplateScene;
import com.openhis.health.domain.share.enums.OrderTemplateStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 医嘱组套模板头聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "cus_order_template")
public class OrderTemplate extends BaseAuditedEntity {

    /**
     * 归属对象标识。
     */
    @Column(name = "object_id", nullable = false)
    private Long objectId;

    /**
     * 绑定类型（个人/科室/全院）。
     */
    @Column(name = "binding_enum", nullable = false)
    private String bindingEnum;

    /**
     * 适用场景（门诊/住院/通用）。
     */
    @Column(name = "scene_enum", nullable = false)
    @Convert(converter = OrderTemplateSceneConverter.class)
    private OrderTemplateScene sceneEnum;

    /**
     * 模板编码（可空，非空时建议唯一）。
     */
    @Column(name = "template_code")
    private String templateCode;

    /**
     * 模板名称。
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * 描述。
     */
    @Column(name = "description")
    private String description;

    /**
     * 列表排序权重（同层级内升序；数值越小越靠前）。
     */
    @Column(name = "sort_weight")
    private Integer sortWeight;

    /**
     * 状态（草稿/启用/停用）。
     */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /**
     * 备注。
     */
    @Column(name = "remark")
    private String remark;

    /**
     * 初始化草稿状态。
     */
    public void initDraft() {
        this.statusEnum = OrderTemplateStatus.DRAFT.getCode();
    }

    /**
     * 断言头必填字段与取值合法（BR-OTL-001/002/005/011）。
     */
    public void assertRequiredFields() {
        if (this.objectId == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "归属对象不能为空");
        }
        if (this.bindingEnum == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "绑定类型不能为空");
        }
        if (this.sceneEnum == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "适用场景不能为空");
        }
        if (this.name == null || this.name.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "模板名称不能为空");
        }
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "状态不能为空");
        }
        if (this.sortWeight != null && this.sortWeight < 0) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "排序权重必须为非负整数");
        }
    }

    /**
     * 断言头允许被修改（已删除禁止写侧操作）。
     */
    public void assertEditable() {
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "已删除模板不允许修改");
        }
    }

    /**
     * 断言允许维护模板行（BR-OTL-018）。
     */
    public void assertLineMaintainable() {
        this.assertEditable();
        if (OrderTemplateStatus.RETIRED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "停用状态禁止维护模板行");
        }
    }

    /**
     * 发布（DRAFT -> ACTIVE）。
     */
    public void publish() {
        this.assertEditable();
        if (!OrderTemplateStatus.DRAFT.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "当前状态不允许发布");
        }
        this.statusEnum = OrderTemplateStatus.ACTIVE.getCode();
    }

    /**
     * 断言当前状态不是 RETIRED（BR-OTL-017：停用状态禁止修改头属性）。
     */
    public void assertNotRetired() {
        if (OrderTemplateStatus.RETIRED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "停用状态禁止修改头属性");
        }
    }

    /**
     * 停用（-> RETIRED）。
     */
    public void retire() {
        this.assertEditable();
        if (OrderTemplateStatus.RETIRED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "当前已是停用状态");
        }
        this.statusEnum = OrderTemplateStatus.RETIRED.getCode();
    }

    /**
     * 逻辑删除头（BR-OTL-007）。
     */
    public void markDeleted() {
        this.setIsDeleted(Boolean.TRUE);
    }
}

