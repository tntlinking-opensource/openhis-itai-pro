/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.medicaltech;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 医技执行科室共看组配置（平台壳；院点可代码写死注入，如国基 CT↔DR）。
 *
 * <p>仅影响查询可见轴与归属门禁；写 Procedure 仍用登录 TECH {@code org_id}。</p>
 * <p>组内为科室 {@code adm_organization.bus_no} 精确匹配（禁止用名称）。
 * 可选 yml {@code zxplat.medical-tech.shared-executing-org-bus-no-groups}；院点优先常量注入。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "zxplat.medical-tech")
public class MedicalTechSharedExecutingOrgProperties {

    /**
     * 共看组：每组多个科室 bus_no；登录科 bus_no 落在某组时，查询可见该组全部科室。
     *
     * <p>空列表=关闭共看（默认单科）。</p>
     */
    private List<List<String>> sharedExecutingOrgBusNoGroups = new ArrayList<>();
}
