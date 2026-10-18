/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.noop.insurance.contract;

import com.zxplat.domain.infrastructure.integration.insurance.contract.InsuranceContractTerminalExtension;

/**
 * 未装配 CHS Module 时的契约终端参数占位（{@code isEnabled()=false}，查询恒 empty）。
 */
public final class NoOpInsuranceContractTerminalExtension implements InsuranceContractTerminalExtension {
}
