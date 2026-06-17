package com.openhis.whale.report.domain.service;

import com.openhis.whale.report.domain.entity.PrintEventLog;
import com.openhis.whale.report.domain.repository.PrintEventLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PrintEventLogManager {

    private final PrintEventLogRepository repository;

    public PrintEventLogManager(PrintEventLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PrintEventLog insert(PrintEventLog log) {
        if (log.getCreatedAt() == null) {
            log.setCreatedAt(LocalDateTime.now());
        }
        if (log.getCopies() == null) {
            log.setCopies(0);
        }
        if (log.getSource() == null || log.getSource().isBlank()) {
            log.setSource("WEB");
        }
        return repository.save(log);
    }
}
