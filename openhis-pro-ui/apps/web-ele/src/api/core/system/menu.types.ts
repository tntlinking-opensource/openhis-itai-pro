/**
 * 菜单数据类型
 */
export interface Menu {
  children: Menu[];
  code: string;
  component: string;
  createdAt: string;
  createdBy: null | string;
  displayName: string;
  externalUrl: null | string;
  icon: string;
  id: string;
  isEnabled: boolean;
  isExternal: boolean;
  isHidden: boolean;
  menuType: string;
  name: string;
  parentDisplayName: string;
  parentId: string;
  path: string;
  permissionName: string;
  remark: string;
  sortOrder: number;
  tenantId: null | number;
  updatedAt: null | string;
  updatedBy: null | string;
}

/**
 * 创建菜单参数类型
 */
export interface CreateMenuParams {
  externalUrl: string;
  code: string;
  isExternal: string;
  displayName: string;
  icon: string;
  remark: string;
  parentId?: string;
  isHidden: string;
  path: string;
  component: string;
  sortOrder: number;
  isEnabled: string;
  name: string;
  menuType: string;
  permissionName: string;
}

/**
 * 更新菜单参数类型
 */
export interface UpdateMenuParams {
  id: string;
  request: {
    /** 为 true 时表示父菜单置空（根节点），与未传 parentId 的局部更新区分 */
    clearParent?: boolean;
    component: string;
    displayName: string;
    externalUrl: string;
    icon: string;
    isEnabled: string;
    isExternal: string;
    isHidden: string;
    menuType: string;
    name: string;
    parentId?: string;
    path: string;
    permissionName: string;
    remark: string;
    sortOrder: number;
  };
}

/**
 * 菜单参数类型（保留用于兼容）
 */
export interface MenuParams {
  id?: string;
  name: string;
  path: string;
  component?: string;
  icon?: string;
  order?: number;
  parentId?: string;
  show?: boolean;
  hideInMenu?: boolean;
  hideInTab?: boolean;
  keepAlive?: boolean;
  activePath?: string;
  redirect?: string;
  meta?: Record<string, unknown>;
}

/**
 * 查询菜单列表参数类型
 */
export interface PageMenuParams {
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
}

/**
 * 菜单选项类型（用于父菜单选择）
 */
export interface MenuOption {
  label: string;
  value: number | string;
  children?: MenuOption[];
}
