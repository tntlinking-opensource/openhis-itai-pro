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

package com.openhis.health.domain.administration.chargeitemdefinition.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 收费项目定义实体：描述某业务实例在特定价类/批次下的可计费项与价格。
 */
@Getter
@Setter
@Entity
@Table(name = "adm_charge_item_definition")
public class ChargeItemDefinition extends BaseAuditedEntity {
    /** 实例来源表标识（与多态定价来源对应）。 */
    @Column(name = "instance_table")
    private String instanceTable;
    /** 业务实例主键。 */
    @Column(name = "instance_id", nullable = false)
    private Long instanceId;
    /** 收费展示名称。 */
    @Column(name = "charge_name")
    private String chargeName;
    /** 状态枚举编码。 */
    @Column(name = "status_enum")
    private String statusEnum;
    /** 所属机构主键（可空表示全局）。 */
    @Column(name = "org_id")
    private Long orgId;
    /** 收费类型编码。 */
    @Column(name = "type_code")
    private String typeCode;
    /** 医保类别或对照编码。 */
    @Column(name = "yb_type")
    private String ybType;
    /** 价格类型编码（必填）。 */
    @Column(name = "price_type_code", nullable = false)
    private String priceTypeCode;
    /** 批次或效期批次号。 */
    @Column(name = "lot_no")
    private String lotNo;
    /** 价格数值（元，与 {@code adm_charge_item} 金额列同口径）。 */
    @Column(name = "price_value", nullable = false, precision = 19, scale = 6)
    private BigDecimal priceValue;
    /** 同实例多价时的优先级（数值越小越优先，由产品约定）。 */
    @Column(name = "priority")
    private Integer priority;
}
