export * from './create-icon';

export * from './lucide';

export type { IconifyIcon as IconifyIconStructure } from '@iconify/vue';
export { addCollection, addIcon, Icon as IconifyIcon, listIcons } from '@iconify/vue';

// 预加载 MDI 图标集
import { addCollection } from '@iconify/vue';
import mdiIcons from '@iconify-json/mdi/icons.json';

// 注册 MDI 图标集
addCollection(mdiIcons as any);
