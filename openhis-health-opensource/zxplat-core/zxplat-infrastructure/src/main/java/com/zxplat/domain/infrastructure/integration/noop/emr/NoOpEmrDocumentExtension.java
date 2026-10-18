/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.noop.emr;

import com.zxplat.domain.infrastructure.integration.emr.EmrDocumentExtension;

/**
 * 未装配 EMR Module 时的默认 {@link EmrDocumentExtension}。
 *
 * <p>{@link EmrDocumentExtension#isEnabled()} 继承接口默认 false；文书创建/归档走本地占位，
 * 不调用外部 EMR 服务。</p>
 */
public final class NoOpEmrDocumentExtension implements EmrDocumentExtension {
}
