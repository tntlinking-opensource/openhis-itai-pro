package com.openhis.whale.mrms.application.support.noop;

import com.openhis.whale.mrms.domain.port.IcdSearchPort;
import com.openhis.whale.mrms.domain.port.model.IcdItemSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@ConditionalOnMissingBean(IcdSearchPort.class)
public class NoOpIcdSearchPort implements IcdSearchPort {

    @Override
    public List<IcdItemSnapshot> searchIcd10(String keyword, int limit) {
        return Collections.emptyList();
    }

    @Override
    public List<IcdItemSnapshot> searchIcd9Cm3(String keyword, int limit) {
        return Collections.emptyList();
    }
}
