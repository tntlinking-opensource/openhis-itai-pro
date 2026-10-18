/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.booking.ExtensionOut;

import lombok.Getter;
import lombok.Setter;

/**
 * 当日可约号源类型选项快照（IF-REG-01 收窄）。
 *
 * <p>职责：供挂号台下拉与医师可接诊配置求交，仅展示 booking 已放号的号源类型。</p>
 */
@Getter
@Setter
public class BookableServiceOptionQueryExtensionOut {

    /** 号源类型主键 {@code adm_healthcare_service.id}。 */
    private Long healthcareServiceId;

    /** 号源类型展示名（如专家号、普通号）。 */
    private String serviceTypeName;
}
