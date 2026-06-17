package com.openhis.whale.booking.domain.channel.repository;

import com.openhis.whale.booking.domain.channel.entity.ChannelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ChannelConfigRepository extends JpaRepository<ChannelConfig, Long>, JpaSpecificationExecutor<ChannelConfig> {

    Optional<ChannelConfig> findByOrganizationIdAndChannelCodeAndIsDeletedFalse(Long organizationId, String channelCode);
}
