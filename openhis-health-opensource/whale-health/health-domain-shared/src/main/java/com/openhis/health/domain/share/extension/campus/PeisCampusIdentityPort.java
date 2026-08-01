/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.extension.campus;

import java.util.Optional;

/**
 * PEIS → Campus 身份解析 SPI。
 *
 * <p>Campus 未启用时无 Bean；调用方须 {@code ObjectProvider} 可选注入。</p>
 */
public interface PeisCampusIdentityPort {

    Optional<PeisCampusIdentityView> resolveByPatientId(Long tenantId, Long patientId);

    Optional<PeisCampusIdentityView> resolveByStudentNo(Long tenantId, String studentNo);

    Optional<PeisCampusIdentityView> resolveByEmployeeNo(Long tenantId, String employeeNo);
}
