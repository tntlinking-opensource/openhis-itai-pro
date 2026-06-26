package com.openhis.whale.booking.domain.diagnostic.service;

import com.openhis.whale.booking.domain.diagnostic.entity.DiagnosticResourceMap;
import com.openhis.whale.booking.domain.diagnostic.repository.DiagnosticResourceMapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 医技资源映射写侧（医嘱项目与可预约资源对照）。
 */
@Service
@RequiredArgsConstructor
public class DiagnosticResourceMapDomainService {

    private final DiagnosticResourceMapRepository repository;

    /**
     * 保存医技资源映射。
     *
     * @param entity 映射实体
     * @param operatorId 操作人
     * @return 落库后的实体
     */
    public DiagnosticResourceMap save(DiagnosticResourceMap entity, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        if (entity.getId() == null) {
            entity.setCreatedAt(now);
            entity.setCreatedBy(operatorId);
            entity.setIsDeleted(Boolean.FALSE);
        }
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operatorId);
        // 持久化
        return repository.save(entity);
    }

    /**
     * 软删医技资源映射。
     *
     * @param id 映射主键
     * @param operatorId 操作人
     */
    public void softDelete(Long id, Long operatorId) {
        repository.findById(id).ifPresent(entity -> {
            entity.setIsDeleted(Boolean.TRUE);
            entity.setDeletedAt(LocalDateTime.now());
            entity.setDeletedBy(operatorId);
            repository.save(entity);
        });
    }
}
