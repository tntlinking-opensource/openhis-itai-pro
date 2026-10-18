/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.domain.wechataccount.entity;

import java.time.LocalDateTime;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 微信账号（map_wechat_account）。
 */
@Getter
@Setter
@Entity
@Table(name = "map_wechat_account")
public class MapWechatAccount extends BaseAuditedEntity {

    @Column(name = "app_id", nullable = false, length = 64)
    private String appId;

    @Column(name = "openid", nullable = false, length = 64)
    private String openid;

    @Column(name = "union_id", length = 64)
    private String unionId;

    @Column(name = "status_enum", nullable = false, length = 32)
    private String statusEnum = "active";

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
