/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.mrms.qc.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 质控规则元数据（{@code mrms_qc_rule}）；种子租户 + 租户自定义，同 rule_code 租户行覆盖种子。
 *
 * <p>{@code tenant_id} 为 {@link com.openhis.health.domain.share.constants.GeneralSharedConstants#PLATFORM_TENANT_ID}
 * 时表示 Flyway 全局种子；业务租户可插入同 {@code rule_code} 行覆盖默认配置。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_qc_rule")
public class QcRule extends BaseAuditedEntity {

    /** 规则唯一 code，与执行器分支对应。 */
    @Column(name = "rule_code", nullable = false, length = 64)
    private String ruleCode;

    /** 规则展示名称（病案室配置页）。 */
    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    /** 默认严重级别 code（规则命中新建缺陷时的缺省值）。 */
    @Column(name = "severity_code", nullable = false, length = 16)
    private String severityCode;

    /** 规则分类 code（首页完整性/文书/时效等，供配置页分组）。 */
    @Column(name = "category_code", nullable = false, length = 32)
    private String categoryCode;

    /** 租户是否启用该规则（false 时执行器跳过）。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 扩展表达式 JSON（预留，当前执行器以 rule_code 硬编码为主）。 */
    @Column(name = "expression_json", columnDefinition = "TEXT")
    private String expressionJson;
}
