package com.openhis.whale.report.domain.repository;

import com.openhis.whale.report.domain.entity.PrintBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrintBindingRepository extends JpaRepository<PrintBinding, Long> {

    Optional<PrintBinding> findByTenantIdAndBindingCodeAndIsDeletedFalse(Long tenantId, String bindingCode);

    List<PrintBinding> findByTenantIdAndIsDeletedFalseOrderByBindingCodeAsc(Long tenantId);
}
