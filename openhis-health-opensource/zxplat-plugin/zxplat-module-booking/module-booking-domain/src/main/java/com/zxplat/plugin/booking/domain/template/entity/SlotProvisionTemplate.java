/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.template.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T1 放号时段模板（bkg_slot_provision_template，07 §5.2）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_slot_provision_template")
public class SlotProvisionTemplate extends BaseAuditedEntity {

    /** 科室主键；可空表示全院模板。 */
    @Column(name = "organization_id")
    private Long organizationId;

    /** 模板编码。 */
    @Column(name = "template_code", nullable = false)
    private String templateCode;

    /** 模板名称。 */
    @Column(name = "template_name", nullable = false)
    private String templateName;

    /** 时段 JSON：[{startTime,endTime,capacity}]。 */
    @Column(name = "slot_templates_json", nullable = false)
    private String slotTemplatesJson;

    /** 是否启用。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
