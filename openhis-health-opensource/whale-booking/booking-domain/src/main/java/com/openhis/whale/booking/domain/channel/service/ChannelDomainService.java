package com.openhis.whale.booking.domain.channel.service;

import com.openhis.whale.booking.domain.channel.entity.ChannelConfig;
import com.openhis.whale.booking.domain.channel.repository.ChannelConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChannelDomainService {

    private final ChannelConfigRepository repository;

    public ChannelConfig save(ChannelConfig config, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        if (config.getId() == null) {
            config.setCreatedAt(now);
            config.setCreatedBy(operatorId);
            config.setIsDeleted(Boolean.FALSE);
        }
        config.setUpdatedAt(now);
        config.setUpdatedBy(operatorId);
        return repository.save(config);
    }

    public void softDelete(Long id, Long operatorId) {
        repository.findById(id).ifPresent(config -> {
            config.setIsDeleted(Boolean.TRUE);
            config.setDeletedAt(LocalDateTime.now());
            config.setDeletedBy(operatorId);
            repository.save(config);
        });
    }
}
