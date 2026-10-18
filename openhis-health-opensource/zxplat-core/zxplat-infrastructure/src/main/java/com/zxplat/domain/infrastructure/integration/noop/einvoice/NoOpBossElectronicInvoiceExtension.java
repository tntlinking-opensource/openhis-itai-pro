/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.noop.einvoice;

import com.zxplat.domain.infrastructure.integration.einvoice.BossElectronicInvoiceExtension;

/**
 * 未装配 einvoice-boss Module 时的默认 {@link BossElectronicInvoiceExtension}。
 *
 * <p>{@link BossElectronicInvoiceExtension#isEnabled()} 继承接口默认 false；
 * {@code findDocumentByPaymentId} 恒 empty；
 * {@link com.zxplat.domain.infrastructure.integration.einvoice.orchestration.BossElectronicInvoiceAfterCommitOrchestration}
 * 跳过 afterCommit 开票/冲红，本地票据仍正常落库。</p>
 *
 * <p>只由 {@code IntegrationSpiAutoConfiguration#noOpBossElectronicInvoiceExtension} 注册。
 * 不要加 {@code @Component}：组件扫描会把本类收成同名 Bean，
 * 再和自动配置上的 {@code @ConditionalOnMissingBean} 互相判定已存在，最后两个都不装配。</p>
 */
public final class NoOpBossElectronicInvoiceExtension implements BossElectronicInvoiceExtension {
}
