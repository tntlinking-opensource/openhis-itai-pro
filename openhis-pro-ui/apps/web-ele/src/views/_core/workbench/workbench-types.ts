/** 工作台角色分组标识 */
export type WorkbenchGroupId = 'admin' | 'cashier' | 'custom' | 'doctor' | 'nurse' | 'pharmacy';

/** 工作台分组配置（menuCodes 对应后端菜单 code / 路由 name） */
export interface WorkbenchGroupConfig {
  accentColor: string;
  accentGradient: string;
  icon: string;
  iconBgColors: string[];
  iconColors: string[];
  id: WorkbenchGroupId;
  menuCodes: string[];
  subtitle: string;
  title: string;
}

/** 解析后的快捷入口项 */
export interface WorkbenchShortcutItem {
  accentColor: string;
  cardTint: string;
  groupId: WorkbenchGroupId;
  icon?: string;
  iconBgColor: string;
  iconColor: string;
  iconGradient: string;
  path: string;
  routeName: string;
  title: string;
}

/** 带可见入口的分组 */
export interface WorkbenchGroupView {
  config: WorkbenchGroupConfig;
  items: WorkbenchShortcutItem[];
}
