package com.openhis.whale.booking.adapter.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "com.openhis.whale.booking.contracts.reader.OutpatientBookingReader")
@ConditionalOnProperty(name = "openhis.modules.booking.enabled", havingValue = "true")
@AutoConfigureBefore(name = "com.openhis.whale.booking.application.config.BookingAutoConfiguration")
@ComponentScan(basePackages = {
        "com.openhis.whale.booking.adapter",
        "com.openhis.health.integration.booking"
})
public class BookingAdapterAutoConfiguration {
}
