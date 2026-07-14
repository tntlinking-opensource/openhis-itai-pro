/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.noop.emr;

import com.openhis.health.infrastructure.integration.emr.EmrAdmissionDiagnosisSyncExtension;

/**
 * 未装配 EMR Module 时的默认 {@link EmrAdmissionDiagnosisSyncExtension}。
 *
 * <p>{@link EmrAdmissionDiagnosisSyncExtension#isEnabled()} 继承接口默认 false；
 * 入院诊断不同步 EMR，住院侧仅保留 HIS 诊断数据。</p>
 */
public final class NoOpEmrAdmissionDiagnosisSyncExtension implements EmrAdmissionDiagnosisSyncExtension {
}
