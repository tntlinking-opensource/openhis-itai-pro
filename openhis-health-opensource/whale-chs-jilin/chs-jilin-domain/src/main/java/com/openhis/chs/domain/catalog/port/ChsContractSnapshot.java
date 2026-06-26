package com.openhis.chs.domain.catalog.port;

/**
 * 医保业务与 HIS 费用性质（{@code fin_contract}）的关联键。
 *
 * <p><b>仅 {@code id}、{@code busNo} 来自主数据表</b>：用于租户内契约主键、目录游标、
 * 与 {@code chs-contract.properties} 配置段 {@code contract.{bus_no}.*} 对齐。</p>
 *
 * <p>定点医疗机构编码、统筹区划、OAuth 密钥、网关 URL、签到 MAC 等<strong>一律不读
 * {@code fin_contract}</strong>，由 {@link ChsContractPropertiesPort} 按 {@code busNo} 加载。</p>
 */
public record ChsContractSnapshot(Long id, String busNo) {
}
