package com.openhis.whale.booking.domain.diagnostic.service;

import com.openhis.whale.booking.domain.diagnostic.entity.DiagnosticResourceMap;
import com.openhis.whale.booking.domain.diagnostic.repository.DiagnosticResourceMapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DiagnosticResourceMapDomainService {

    private final DiagnosticResourceMapRepository repository;

    public DiagnosticResourceMap save(DiagnosticResourceMap entity, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        if (entity.getId() == null) {
            entity.setCreatedAt(now);
            entity.setCreatedBy(operatorId);
            entity.setIsDeleted(Boolean.FALSE);
        }
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operatorId);
        return repository.save(entity);
    }

    public void softDelete(Long id, Long operatorId) {
        repository.findById(id).ifPresent(entity -> {
            entity.setIsDeleted(Boolean.TRUE);
            entity.setDeletedAt(LocalDateTime.now());
            entity.setDeletedBy(operatorId);
            repository.save(entity);
        });
    }
}
