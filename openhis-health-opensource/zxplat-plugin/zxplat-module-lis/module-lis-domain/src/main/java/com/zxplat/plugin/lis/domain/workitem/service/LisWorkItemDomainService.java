/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.lis.domain.workitem.service;

import com.zxplat.plugin.lis.domain.workitem.repository.LisWorkItemOrderRepository;
import com.zxplat.plugin.lis.domain.workitem.repository.LisWorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 检验工单领域服务（P0 骨架）。
 */
@Service
@RequiredArgsConstructor
public class LisWorkItemDomainService {

    private final LisWorkItemRepository workItemRepository;
    private final LisWorkItemOrderRepository orderRepository;
}