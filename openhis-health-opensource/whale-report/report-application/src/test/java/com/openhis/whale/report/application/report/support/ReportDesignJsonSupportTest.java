package com.openhis.whale.report.application.report.support;

import com.openhis.whale.report.application.report.response.ReportFilterFieldDto;
import com.openhis.whale.report.application.report.response.ReportFooterConfigDto;
import com.openhis.whale.report.application.report.response.ReportHeaderConfigDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ReportDesignJsonSupportTest {

    @Test
    void parseHeaderConfigReadsTitleAndLines() {
        String json =
                """
                {"title":"日结报表","subtitle":"副标题","lines":[{"type":"text","text":"行1","align":"center"}]}
                """;
        ReportHeaderConfigDto header = ReportDesignJsonSupport.parseHeaderConfig(json);
        assertEquals("日结报表", header.getTitle());
        assertEquals(1, header.getLines().size());
        assertEquals("text", header.getLines().get(0).getType());
    }

    @Test
    void parseFooterConfigReadsNotesAndLines() {
        String json = "{\"notes\":\"制表人\",\"lines\":[\"行A\",\"行B\"]}";
        ReportFooterConfigDto footer = ReportDesignJsonSupport.parseFooterConfig(json);
        assertEquals("制表人", footer.getNotes());
        assertEquals(2, footer.getLines().size());
    }

    @Test
    void parseFiltersDefaultsWhenEmpty() {
        var filters = ReportDesignJsonSupport.parseFilters(null);
        assertFalse(filters.isEmpty());
        assertEquals("dateFrom", filters.get(0).getField());
    }

    @Test
    void parseFiltersReadsCustomFields() {
        String json = "[{\"field\":\"keyword\",\"label\":\"关键词\",\"type\":\"text\"}]";
        var filters = ReportDesignJsonSupport.parseFilters(json);
        assertEquals(1, filters.size());
        ReportFilterFieldDto field = filters.get(0);
        assertEquals("keyword", field.getField());
        assertEquals("text", field.getType());
    }
}
