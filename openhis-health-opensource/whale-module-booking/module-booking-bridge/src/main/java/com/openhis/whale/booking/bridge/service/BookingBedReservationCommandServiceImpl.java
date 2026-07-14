/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.bridge.service;

import com.openhis.health.domain.administration.location.service.LocationDomainService;
import com.openhis.whale.booking.domain.bed.service.BookingBedReservationCommandService;
import com.openhis.whale.booking.domain.bed.command.ReleaseBedReservationCommand;
import com.openhis.whale.booking.domain.bed.command.ReserveBedCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingBedReservationCommandServiceImpl implements BookingBedReservationCommandService {

    private final LocationDomainService locationDomainService;

    @Override
    public void reserveBed(ReserveBedCommand command) {
        if (command == null) {
            return;
        }
        com.openhis.health.domain.administration.location.command.ReserveBedCommand healthCommand =
            new com.openhis.health.domain.administration.location.command.ReserveBedCommand();
        healthCommand.setOperatorId(command.getOperatorId());
        List<com.openhis.health.domain.administration.location.command.ReserveBedCommand.ReserveBedItemCommand> items =
            new ArrayList<>();
        if (command.getItems() != null) {
            for (ReserveBedCommand.ReserveBedItemCommand item : command.getItems()) {
                if (item == null || item.getLocationId() == null) {
                    continue;
                }
                com.openhis.health.domain.administration.location.command.ReserveBedCommand.ReserveBedItemCommand healthItem =
                    new com.openhis.health.domain.administration.location.command.ReserveBedCommand.ReserveBedItemCommand();
                healthItem.setLocationId(item.getLocationId());
                healthItem.setReason(item.getReason());
                items.add(healthItem);
            }
        }
        healthCommand.setItems(items);
        if (items.isEmpty()) {
            return;
        }
        locationDomainService.reserveBed(healthCommand);
    }

    @Override
    public void releaseBedReservation(ReleaseBedReservationCommand command) {
        if (command == null) {
            return;
        }
        com.openhis.health.domain.administration.location.command.ReleaseBedCommand healthCommand =
            new com.openhis.health.domain.administration.location.command.ReleaseBedCommand();
        healthCommand.setOperatorId(command.getOperatorId());
        List<com.openhis.health.domain.administration.location.command.ReleaseBedCommand.ReleaseBedItemCommand> items =
            new ArrayList<>();
        if (command.getItems() != null) {
            for (ReleaseBedReservationCommand.ReleaseBedReservationItemCommand item : command.getItems()) {
                if (item == null || item.getLocationId() == null) {
                    continue;
                }
                com.openhis.health.domain.administration.location.command.ReleaseBedCommand.ReleaseBedItemCommand healthItem =
                    new com.openhis.health.domain.administration.location.command.ReleaseBedCommand.ReleaseBedItemCommand();
                healthItem.setLocationId(item.getLocationId());
                items.add(healthItem);
            }
        }
        healthCommand.setItems(items);
        if (items.isEmpty()) {
            return;
        }
        locationDomainService.releaseReservedBed(healthCommand);
    }
}
