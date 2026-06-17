package com.openhis.whale.mrms.application.integration;

import com.openhis.whale.mrms.port.CaseRecordBootstrapPort;
import com.openhis.whale.mrms.domain.service.CaseRecordManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 离院等 HIS 事件触发的病案主档幂等创建（委托 {@link CaseRecordManager}）。
 */
@Component
@RequiredArgsConstructor
public class CaseRecordBootstrapPortImpl implements CaseRecordBootstrapPort {

    private final CaseRecordManager caseRecordManager;

    @Override
    public void createIfAbsent(long encounterId) {
        caseRecordManager.createIfAbsent(encounterId);
    }
}
