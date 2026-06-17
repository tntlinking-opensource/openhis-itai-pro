package com.openhis.whale.report.application.pdf.overlay;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.report.application.layout.model.LayoutBox;
import com.openhis.whale.report.application.layout.model.LayoutItem;
import com.openhis.whale.report.application.layout.model.LayoutPage;
import com.openhis.whale.report.application.layout.support.PositionedElementSupport;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import org.springframework.stereotype.Component;

@Component
public class OverlayBoxResolver {

    public LayoutBox resolveEffectiveBox(LayoutItem item, PrintElementDef def, LayoutPage page) {
        LayoutBox effective = PositionedElementSupport.resolveEffectiveBox(item, def, page);
        if (effective == null || !PositionedElementSupport.hasBoxGeometry(effective)) {
            String elementCode = "";
            if (item != null) {
                elementCode = item.getElementCode();
            }
            throw new BusinessException(
                    "RPT_OVERLAY_BOX_MISSING",
                    "套打元素缺少 box：elementCode=" + elementCode);
        }
        return effective;
    }
}
