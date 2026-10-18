/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.support;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 手术/麻醉申请：麻醉医师下拉所属科室（按 {@code adm_organization.bus_no}）。
 *
 * <p>空列表=未配置，Init 不返回麻醉医师选项（前端可回落）。院点优先常量注入，禁止按名称匹配。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "zxplat.inpatient.surgery-anesth")
public class SurgeryAnesthAnesthetistOrgProperties {

    /**
     * 麻醉医师出诊科室 bus_no 列表（如麻醉室）。
     *
     * <p>有值时按 {@code ROLE_DOCTOR} + {@code adm_pr_assign.org_id} 收窄，与用户管理「出诊科室」同口径。</p>
     */
    private List<String> anesthesiaOrgBusNos = new ArrayList<>();
}
