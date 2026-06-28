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

package com.openhis.health.domain.print.printbinding.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.core.domain.IMultiTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 业务场景模板绑定聚合根（DM-PRT-003 · PrintBinding）。
 *
 * <p>对应表 {@code prt_print_binding}；租户 override 覆盖平台 seed 定义。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "prt_print_binding")
public class PrtPrintBinding extends BaseAuditedEntity implements IMultiTenant {

    /** 机构主键（本期租户级绑定恒 NULL）。 */
    @Column(name = "organization_id")
    private Long organizationId;

    /** 场景入口码（如 {@code outpatient.rx.print}）。 */
    @Column(name = "binding_code", nullable = false, length = 64)
    private String bindingCode;

    /** 绑定的模板 busNo。 */
    @Column(name = "bus_no", nullable = false, length = 64)
    private String busNo;

    /** 绑定展示名称（平台 seed 用）。 */
    @Column(name = "name", length = 128)
    private String name;

    /** 是否启用；禁用时不参与 resolve。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
