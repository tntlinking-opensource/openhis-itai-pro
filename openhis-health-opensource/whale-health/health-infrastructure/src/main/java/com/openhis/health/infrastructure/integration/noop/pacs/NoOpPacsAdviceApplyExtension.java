/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.noop.pacs;

import com.openhis.health.infrastructure.integration.pacs.PacsAdviceApplyExtension;

/**
 * 未装配 PACS Module 时的默认 {@link PacsAdviceApplyExtension}。
 *
 * <p>{@link PacsAdviceApplyExtension#isEnabled()} 继承接口默认 false；检查医嘱签发后不推送 PACS 申请，
 * 本地医嘱与收费不受影响。</p>
 */
public final class NoOpPacsAdviceApplyExtension implements PacsAdviceApplyExtension {
}
