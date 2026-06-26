/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.support.noop;

import com.openhis.whale.mrms.contracts.reader.HealthIcdDictionaryReader;
import com.openhis.whale.mrms.contracts.snapshot.IcdItemSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 无 HIS 适配器时的 {@link HealthIcdDictionaryReader} 空实现。
 */
@Component
@ConditionalOnMissingBean(HealthIcdDictionaryReader.class)
public class NoOpHealthIcdDictionaryReader implements HealthIcdDictionaryReader {

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IcdItemSnapshot> searchIcd10(String keyword, int limit) {
        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IcdItemSnapshot> searchIcd9Cm3(String keyword, int limit) {
        return Collections.emptyList();
    }
}
