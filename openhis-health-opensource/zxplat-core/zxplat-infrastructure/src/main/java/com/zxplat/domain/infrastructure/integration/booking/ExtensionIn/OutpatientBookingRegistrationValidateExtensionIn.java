/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.booking.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

/**
 * 预约转挂号前的交叉校验命令（IF-REG-13）。
 *
 * <p>职责：挂号台提交科室/医师/号源类型须与预约快照一致，防止错挂他人预约或跨科室占号。</p>
 */
@Getter
@Setter
public class OutpatientBookingRegistrationValidateExtensionIn {

    /** 预约主键。 */
    private Long appointmentId;

    /** 挂号科室主键，须与预约 organization 一致。 */
    private Long organizationId;

    /** 挂号医师主键，须与预约 practitioner 一致。 */
    private Long practitionerId;

    /** 号源类型主键，须与预约 healthcareService 一致。 */
    private Long healthcareServiceId;

    /** 患者主键，须与预约归属患者一致。 */
    private Long patientId;
}
