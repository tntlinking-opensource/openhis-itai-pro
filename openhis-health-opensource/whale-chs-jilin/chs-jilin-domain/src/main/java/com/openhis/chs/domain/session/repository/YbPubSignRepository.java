/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.session.repository;

import com.openhis.chs.domain.session.entity.YbPubSign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface YbPubSignRepository extends JpaRepository<YbPubSign, Long> {

    Optional<YbPubSign> findFirstByContractBusNoAndOpterNoAndSignDateAndStatusAndTenantIdAndIsDeletedFalseOrderBySignTimeDesc(
        String contractBusNo, String opterNo, LocalDate signDate, String status, Long tenantId);

    Optional<YbPubSign> findFirstByContractBusNoAndSignDateAndStatusAndTenantIdAndIsDeletedFalseOrderBySignTimeDesc(
        String contractBusNo, LocalDate signDate, String status, Long tenantId);
}
