import { h } from 'vue';

import { CircleClose, InfoFilled, SuccessFilled, WarningFilled } from '@element-plus/icons-vue';
import { ElIcon } from 'element-plus';

/**
 * 获取 ElMessageBox 的自定义图标
 * @param type - 图标类型
 * @param size - 图标大小，默认 28
 * @returns VNode
 */
export function getMessageBoxIcon(
  type: 'error' | 'info' | 'success' | 'warning',
  size: number = 28,
) {
  const iconMap = {
    success: SuccessFilled,
    warning: WarningFilled,
    info: InfoFilled,
    error: CircleClose,
  };

  const colorMap = {
    success: 'var(--el-color-success)',
    warning: 'var(--el-color-warning)',
    info: 'var(--el-color-info)',
    error: 'var(--el-color-error)',
  };

  const IconComponent = iconMap[type];
  const color = colorMap[type];

  return h(ElIcon, { size, style: { color } }, () => h(IconComponent));
}
