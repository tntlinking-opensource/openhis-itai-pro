/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.domain.wechataccount.repository;

import java.util.Optional;

import com.openhis.miniapp.domain.wechataccount.entity.MapWechatAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapWechatAccountRepository extends JpaRepository<MapWechatAccount, Long> {

    Optional<MapWechatAccount> findFirstByTenantIdAndAppIdAndOpenidAndIsDeletedFalse(
            Long tenantId, String appId, String openid);
}
