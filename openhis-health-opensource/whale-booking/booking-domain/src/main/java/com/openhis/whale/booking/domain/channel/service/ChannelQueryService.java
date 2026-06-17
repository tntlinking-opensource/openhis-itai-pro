package com.openhis.whale.booking.domain.channel.service;

import com.openhis.whale.booking.domain.channel.entity.ChannelConfig;
import com.openhis.whale.booking.domain.channel.repository.ChannelConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChannelQueryService {

    private final ChannelConfigRepository repository;

    public Page<ChannelConfig> pageByOrganization(Long organizationId, Pageable pageable) {
        Specification<ChannelConfig> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            predicates.getExpressions().add(cb.equal(root.get("isDeleted"), false));
            if (organizationId != null) {
                predicates.getExpressions().add(cb.equal(root.get("organizationId"), organizationId));
            }
            return predicates;
        };
        return repository.findAll(spec, pageable);
    }

    public Optional<ChannelConfig> findByOrganizationAndCode(Long organizationId, String channelCode) {
        return repository.findByOrganizationIdAndChannelCodeAndIsDeletedFalse(organizationId, channelCode);
    }

    public ChannelConfig loadById(Long id) {
        ChannelConfig config = repository.findById(id).orElse(null);
        if (config == null || Boolean.TRUE.equals(config.getIsDeleted())) {
            return null;
        }
        return config;
    }
}
