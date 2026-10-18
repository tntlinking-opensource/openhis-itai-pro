/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import lombok.Data;

/**
 * 选择医院请求（写入服务端会话）。
 */
@Data
public class SelectHospitalRequest {

    /**
     * HIS 医院组织主键 {@code adm_organization.id}
     */
    private Long hospitalId;
}
