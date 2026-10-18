/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.healthcheckup.repository;

import com.zxplat.plugin.booking.domain.healthcheckup.entity.HcDayType;
import com.zxplat.framework.core.infrastructure.transaction.SkipSoftDeleteFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 体检日开放合同类型仓储。
 */
public interface HcDayTypeRepository extends JpaRepository<HcDayType, Long>, JpaSpecificationExecutor<HcDayType> {

    List<HcDayType> findByDayPlanIdAndIsDeletedFalse(Long dayPlanId);

    Optional<HcDayType> findByDayPlanIdAndContractTypeCodeAndIsDeletedFalse(Long dayPlanId, String contractTypeCode);

    /**
     * 含已软删行，用于避免 UNIQUE(day_plan_id, contract_type_code) 与软删再插冲突。
     */
    @SkipSoftDeleteFilter
    List<HcDayType> findAllByDayPlanId(Long dayPlanId);
}
