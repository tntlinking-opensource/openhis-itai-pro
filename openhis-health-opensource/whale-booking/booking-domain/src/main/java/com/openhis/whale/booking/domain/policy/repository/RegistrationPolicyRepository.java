package com.openhis.whale.booking.domain.policy.repository;

import com.openhis.whale.booking.domain.policy.entity.RegistrationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface RegistrationPolicyRepository extends JpaRepository<RegistrationPolicy, Long>, JpaSpecificationExecutor<RegistrationPolicy> {

    Optional<RegistrationPolicy> findFirstByOrganizationIdAndIsDeletedFalse(Long organizationId);
}
