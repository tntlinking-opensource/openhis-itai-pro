/** 工作台分组标识（管理员固定组 + 按菜单父级动态组） */
export type WorkbenchGroupId = string;

/** 工作台分组配置（menuCodes 对应后端菜单 code / 路由 name；动态组可为空） */
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
