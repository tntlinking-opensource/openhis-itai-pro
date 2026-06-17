package com.openhis.whale.booking.adapter.port;

import com.openhis.health.domain.administration.location.service.LocationDomainService;
import com.openhis.whale.booking.contracts.command.ReleaseBedReservationCommand;
import com.openhis.whale.booking.contracts.command.ReserveBedCommand;
import com.openhis.whale.booking.contracts.port.BookingBedPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link BookingBedPort} 实现：写 health Location 床位运营状态。
 */
@Component
@RequiredArgsConstructor
public class BookingBedPortAdapter implements BookingBedPort {

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
        locationDomainService.releaseBed(healthCommand);
    }
}
