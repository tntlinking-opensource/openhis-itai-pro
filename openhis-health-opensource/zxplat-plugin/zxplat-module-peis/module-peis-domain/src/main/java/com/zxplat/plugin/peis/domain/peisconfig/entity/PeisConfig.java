/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.peisconfig.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * PEIS 模块 KV 参数（表 {@code peis_config}，DM-PEIS-004）。
 */
@Getter
@Setter
@Entity
@Table(name = "peis_config")
public class PeisConfig extends BaseAuditedEntity {

    /** 院区标识。 */
    @Column(name = "org_id")
    private Long orgId;

    /** 配置键。 */
    @Column(name = "config_key", nullable = false)
    private String configKey;

    /** 配置值。 */
    @Column(name = "config_value")
    private String configValue;

    /**
     * 校验键值必填。
     */
    public void assertCoreFields() {
        if (StringUtils.isEmpty(this.configKey) || this.configValue == null) {
            throw new BusinessException(ErrorCodeConstants.PEIS_CONFIG_INVALID_ARGUMENT, "配置键与值不能为空");
        }
    }
}
