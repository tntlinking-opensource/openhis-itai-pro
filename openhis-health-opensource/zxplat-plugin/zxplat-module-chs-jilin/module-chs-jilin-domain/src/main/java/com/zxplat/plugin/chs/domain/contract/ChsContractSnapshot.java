/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.contract;

/**
 * 医保业务与 HIS 费用性质（{@code fin_contract}）的关联键。
 *
 * <p>{@code id} 对应院内契约主键；{@code busNo} 为 {@code chs-contract.properties} 配置键
 * （{@code contract.{bus_no}.*}）。定点编码、就医地、OAuth 等终端参数不从此 record 扩展。</p>
 */
public record ChsContractSnapshot(Long id, String busNo) {
}
