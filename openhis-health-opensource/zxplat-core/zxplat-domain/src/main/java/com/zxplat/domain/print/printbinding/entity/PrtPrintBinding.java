/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.print.printbinding.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import com.zxplat.framework.core.domain.IMultiTenant;
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
