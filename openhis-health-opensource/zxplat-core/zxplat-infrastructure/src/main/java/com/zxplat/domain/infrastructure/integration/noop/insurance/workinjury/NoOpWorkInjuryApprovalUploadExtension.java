/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.noop.insurance.workinjury;

import com.zxplat.domain.infrastructure.integration.workinjury.WorkInjuryApprovalUploadExtension;

/**
 * 未装配 wi-jilin 时的默认 {@link WorkInjuryApprovalUploadExtension}。
 *
 * <p>接口 default 方法为空操作；工伤审批材料不上传平台。</p>
 *
 * <p>吉林 Module 启用时由 {@code WorkInjuryApprovalUploadExtensionImpl}（{@code @Primary}）覆盖。</p>
 */
public final class NoOpWorkInjuryApprovalUploadExtension implements WorkInjuryApprovalUploadExtension {
}
