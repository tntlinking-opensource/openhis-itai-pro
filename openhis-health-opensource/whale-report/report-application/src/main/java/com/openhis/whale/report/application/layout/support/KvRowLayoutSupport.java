package com.openhis.whale.report.application.layout.support;
import com.openhis.whale.report.application.layout.model.LayoutItem;
import com.openhis.whale.report.application.layout.model.KvItemDef;

import com.openhis.whale.report.application.pdf.BindPathResolver;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class KvRowLayoutSupport {

    private KvRowLayoutSupport() {
    }

    public record ResolvedKvItem(KvItemDef def, String value) {
    }

    public static int resolveColumnsPerRow(LayoutItem item) {
        if (item == null || item.getColumns() == null || item.getColumns() < 1) {
            return 1;
        }
        return Math.min(item.getColumns(), 6);
    }

    public static List<ResolvedKvItem> resolveVisibleItems(
            LayoutItem item, Map<String, Object> row, List<KvItemDef> kvItems) {
        List<ResolvedKvItem> visible = new ArrayList<>();
        for (KvItemDef kv : kvItems) {
            if (item != null
                    && item.getHiddenItems() != null
                    && item.getHiddenItems().contains(kv.getField())) {
                continue;
            }
            String val = BindPathResolver.asText(row.get(kv.getField()));
            if (!StringUtils.hasText(val)) {
                continue;
            }
            visible.add(new ResolvedKvItem(kv, val));
        }
        return visible;
    }

    /** 多列布局时，按列统计最长标签字数，保证同列冒号与数值纵向对齐。 */
    public static int[] resolveMaxLabelCharsPerColumn(List<ResolvedKvItem> visible, int columnsPerRow) {
        int[] max = new int[columnsPerRow];
        for (int i = 0; i < visible.size(); i++) {
            int col = i % columnsPerRow;
            String label = visible.get(i).def().getLabel();
            if (StringUtils.hasText(label)) {
                max[col] = Math.max(max[col], label.length());
            }
        }
        return max;
    }

    public static int resolveMaxLabelChars(List<ResolvedKvItem> visible) {
        return Arrays.stream(resolveMaxLabelCharsPerColumn(visible, 1)).max().orElse(0);
    }
}
