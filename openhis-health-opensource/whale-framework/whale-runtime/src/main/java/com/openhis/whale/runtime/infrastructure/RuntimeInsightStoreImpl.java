/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.runtime.infrastructure;

import com.openhis.whale.core.infrastructure.runtime.IRuntimeInsightStore;
import com.openhis.whale.core.infrastructure.runtime.RuntimeInsightInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 框架运行时洞察存储：异步转发至中心节点，任何异常不影响调用方。
 */
@Service
public class RuntimeInsightStoreImpl implements IRuntimeInsightStore {

    private static final Logger log = LoggerFactory.getLogger(RuntimeInsightStoreImpl.class);

    private final RemoteRuntimeSyncClient remoteRuntimeSyncClient;

    public RuntimeInsightStoreImpl(RemoteRuntimeSyncClient remoteRuntimeSyncClient) {
        this.remoteRuntimeSyncClient = remoteRuntimeSyncClient;
    }

    @Override
    public void record(RuntimeInsightInfo info) {
        try {
            remoteRuntimeSyncClient.sync(info);
        } catch (Exception e) {
            log.trace("Framework runtime record dispatch skipped: {}", e.getMessage());
        }
    }

    @Override
    public void recordBatch(List<RuntimeInsightInfo> infos) {
        if (infos == null || infos.isEmpty()) {
            return;
        }
        for (RuntimeInsightInfo info : infos) {
            record(info);
        }
    }
}
