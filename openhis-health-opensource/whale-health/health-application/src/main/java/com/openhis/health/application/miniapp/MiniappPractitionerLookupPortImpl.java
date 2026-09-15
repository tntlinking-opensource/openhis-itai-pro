/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.miniapp;

import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.share.extension.miniapp.MiniappPractitionerLookupPort;
import com.openhis.health.domain.share.extension.miniapp.MiniappPractitionerLookupView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 小程序医护查档 SPI 实现。
 */
@Service
@RequiredArgsConstructor
public class MiniappPractitionerLookupPortImpl implements MiniappPractitionerLookupPort {

    private final PractitionerQueryService practitionerQueryService;

    @Override
    public Optional<MiniappPractitionerLookupView> resolveByBusNo(Long tenantId, String busNo) {
        return practitionerQueryService.findActiveByBusNo(busNo).map(this::toView);
    }

    private MiniappPractitionerLookupView toView(Practitioner practitioner) {
        MiniappPractitionerLookupView view = new MiniappPractitionerLookupView();
        view.setPractitionerId(practitioner.getId());
        view.setName(practitioner.getName());
        view.setBusNo(practitioner.getBusNo());
        return view;
    }
}
