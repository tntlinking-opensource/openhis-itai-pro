package com.openhis.whale.report.domain.repository;

import com.openhis.whale.report.domain.entity.PrintEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PrintEventLogRepository
        extends JpaRepository<PrintEventLog, Long>, JpaSpecificationExecutor<PrintEventLog> {
}
