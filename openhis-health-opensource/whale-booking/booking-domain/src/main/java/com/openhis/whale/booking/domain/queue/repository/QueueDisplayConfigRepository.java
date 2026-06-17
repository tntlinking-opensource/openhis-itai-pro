package com.openhis.whale.booking.domain.queue.repository;

import com.openhis.whale.booking.domain.queue.entity.QueueDisplayConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface QueueDisplayConfigRepository extends JpaRepository<QueueDisplayConfig, Long>, JpaSpecificationExecutor<QueueDisplayConfig> {

    Optional<QueueDisplayConfig> findByOrganizationIdAndLocationIdAndIsDeletedFalse(Long organizationId, Long locationId);

    List<QueueDisplayConfig> findByOrganizationIdAndIsDeletedFalse(Long organizationId);
}
