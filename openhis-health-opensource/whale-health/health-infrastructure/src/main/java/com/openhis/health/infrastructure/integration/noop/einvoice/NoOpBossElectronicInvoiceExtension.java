/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.noop.einvoice;

import com.openhis.health.infrastructure.integration.einvoice.BossElectronicInvoiceExtension;

/**
 * 未装配 einvoice-boss Module 时的默认 {@link BossElectronicInvoiceExtension}。
 *
 * <p>{@link BossElectronicInvoiceExtension#isEnabled()} 继承接口默认 false；
 * {@link com.openhis.health.infrastructure.integration.einvoice.orchestration.BossElectronicInvoiceAfterCommitOrchestration}
 * 跳过 afterCommit 开票/冲红，本地票据仍正常落库。</p>
 */
public final class NoOpBossElectronicInvoiceExtension implements BossElectronicInvoiceExtension {
}
