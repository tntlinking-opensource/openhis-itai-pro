/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.noop.elep;

import com.zxplat.domain.infrastructure.integration.elep.ElepPrescriptionExtension;

/**
 * 未装配电子处方 Module 时的默认 {@link ElepPrescriptionExtension}。
 *
 * <p>{@link ElepPrescriptionExtension#isEnabled()} 继承接口默认 false；处方签发不走国家电子处方平台，
 * 本地处方打印与发药不受影响。</p>
 */
public final class NoOpElepPrescriptionExtension implements ElepPrescriptionExtension {
}
