/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import com.zxplat.framework.core.application.dto.LoginOrganizationOption;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 登录范围一次拉齐：医院列表 +（单院时已写入会话后的）可登录科室。
 *
 * <p>用于压缩「选租户后串行 getHospitals → selectHospital → getDepartments」的 RTT。</p>
 */
@Getter
@Setter
public class LoginScopeBootstrapDto {

    /** 可登录医院 */
    private List<LoginOrganizationOption> hospitals = new ArrayList<>();

    /** 当前会话医院下可登录科室；多医院未选院时为空 */
    private List<OrganizationUnitDto> departments = new ArrayList<>();

    /** 已写入会话的医院 id；未自动选院时为 null */
    private Long selectedHospitalId;
}
