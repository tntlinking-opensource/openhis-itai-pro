package com.openhis.whale.booking.domain.template.support;

import com.openhis.whale.booking.domain.template.queries.model.SlotProvisionItemModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateSlotJsonSupportTest {

    @Test
    void parseSlotItems_emptyReturnsEmptyList() {
        assertThat(TemplateSlotJsonSupport.parseSlotItems(null)).isEmpty();
        assertThat(TemplateSlotJsonSupport.parseSlotItems("  ")).isEmpty();
    }

    @Test
    void parseSlotItems_validJson() {
        String json = """
            [
              {"startTime":"08:00","endTime":"08:30","capacity":10},
              {"startTime":"08:30","endTime":"09:00","capacity":8}
            ]
            """;

        List<SlotProvisionItemModel> items = TemplateSlotJsonSupport.parseSlotItems(json);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getStartTime()).isEqualTo("08:00");
        assertThat(items.get(0).getEndTime()).isEqualTo("08:30");
        assertThat(items.get(0).getCapacity()).isEqualTo(10);
        assertThat(items.get(1).getCapacity()).isEqualTo(8);
    }

    @Test
    void parseSlotItems_invalidJsonThrows() {
        assertThatThrownBy(() -> TemplateSlotJsonSupport.parseSlotItems("{not-json"))
            .hasMessageContaining("slotTemplates 格式非法");
    }
}
