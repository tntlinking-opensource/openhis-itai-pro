/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.domain.practitionerbinding.repository;

import java.util.List;
import java.util.Optional;

import com.zxplat.plugin.miniapp.domain.practitionerbinding.entity.MapPractitionerBinding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapPractitionerBindingRepository extends JpaRepository<MapPractitionerBinding, Long> {

    List<MapPractitionerBinding> findByTenantIdAndWechatAccountIdAndUnboundAtIsNullAndIsDeletedFalse(
            Long tenantId, Long wechatAccountId);

    Optional<MapPractitionerBinding> findFirstByTenantIdAndWechatAccountIdAndPractitionerIdAndUnboundAtIsNullAndIsDeletedFalse(
            Long tenantId, Long wechatAccountId, Long practitionerId);
}
