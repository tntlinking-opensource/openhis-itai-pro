package com.openhis.whale.booking.application.template.support;

import com.openhis.whale.booking.application.template.response.SlotProvisionTemplateItemDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateJsonSupportTest {

    @Test
    void parseSlotItems_blankReturnsEmptyList() {
        assertThat(TemplateJsonSupport.parseSlotItems(null)).isEmpty();
        assertThat(TemplateJsonSupport.parseSlotItems("")).isEmpty();
    }

    @Test
    void writeAndParse_roundTrip() {
        SlotProvisionTemplateItemDto item = new SlotProvisionTemplateItemDto();
        item.setStartTime("08:00");
        item.setEndTime("12:00");
        item.setCapacity(10);

        String json = TemplateJsonSupport.writeSlotItems(List.of(item));
        List<SlotProvisionTemplateItemDto> parsed = TemplateJsonSupport.parseSlotItems(json);

        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).getStartTime()).isEqualTo("08:00");
        assertThat(parsed.get(0).getEndTime()).isEqualTo("12:00");
        assertThat(parsed.get(0).getCapacity()).isEqualTo(10);
    }
}
