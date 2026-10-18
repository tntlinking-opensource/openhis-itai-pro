/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.domain.patientbinding.repository;

import java.util.List;
import java.util.Optional;

import com.zxplat.plugin.miniapp.domain.patientbinding.entity.MapPatientBinding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapPatientBindingRepository extends JpaRepository<MapPatientBinding, Long> {

    List<MapPatientBinding> findByTenantIdAndWechatAccountIdAndUnboundAtIsNullAndIsDeletedFalse(
            Long tenantId, Long wechatAccountId);

    Optional<MapPatientBinding> findFirstByTenantIdAndWechatAccountIdAndPatientIdAndUnboundAtIsNullAndIsDeletedFalse(
            Long tenantId, Long wechatAccountId, Long patientId);
}
