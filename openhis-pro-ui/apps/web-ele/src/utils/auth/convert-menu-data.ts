/**
 * 转换后端菜单数据格式为 Vben 需要的格式
 * 根据新的菜单数据结构进行转换
 */
export function convertMenuData(
  menus: any[],
  parentNames: string[] = [],
  allNames: Set<string> = new Set(),
): any[] {
  const result = menus
    .filter((menu) => menu.isEnabled !== false)
    .map((menu) => {
      allNames.add(menu.code);
      const currentParentNames = [...parentNames, menu.code];
      const converted: any = {
        name: menu.code,
        path: menu.path || '/',
        meta: {
          title: menu.displayName || menu.name || '',
          icon: menu.icon || undefined,
          hideInMenu: menu.isHidden === true,
          hideInBreadcrumb: menu.isHidden === true,
          keepAlive: menu.keepAlive === undefined ? false : menu.keepAlive,
        },
      };

      // 处理外部链接
      if (menu.isExternal === true && menu.externalUrl) {
        converted.meta.externalUrl = menu.externalUrl;
        // 外部链接通常不需要 component
        converted.component = undefined;
      } else {
        // 处理 component 映射
        if (menu.component && menu.component.trim() !== '') {
          // 如果 component 以 #/views/ 开头，去掉 # 前缀
          let componentPath = menu.component;
          if (componentPath.startsWith('#/views/')) {
            componentPath = componentPath.replace('#/views/', '');
          } else if (componentPath.startsWith('#/chs/')) {
            componentPath = componentPath.replace('#/chs/', 'chs/');
          } else if (componentPath.startsWith('#/')) {
            componentPath = componentPath.replace('#/', '');
          } else if (componentPath.startsWith('/views/')) {
            componentPath = componentPath.replace('/views/', '');
          } else if (componentPath.startsWith('/chs/')) {
            componentPath = componentPath.replace('/chs/', 'chs/');
          }

          // 如果 component 路径存在，设置它
          if (componentPath && componentPath.trim() !== '') {
            if (!componentPath.endsWith('.vue')) {
              componentPath = `${componentPath}.vue`;
            }
            converted.component = componentPath;
          } else {
            // 如果没有 component 但有子菜单，使用 BasicLayout
            if (menu.children && menu.children.length > 0) {
              converted.component = '';
            }
          }
        } else {
          converted.component = menu.children && menu.children.length > 0 ? '' : undefined;
        }
      }

      // 处理 permissionName
      if (menu.permissionName && menu.permissionName.trim() !== '') {
        converted.meta.permission = menu.permissionName;
      }

      // 处理 sortOrder（用于菜单排序）
      if (menu.sortOrder !== undefined && menu.sortOrder !== null) {
        converted.meta.orderNo = menu.sortOrder;
      }

      // 处理 remark（备注信息）
      if (menu.remark && menu.remark.trim() !== '') {
        converted.meta.remark = menu.remark;
      }

      // 处理 menuType
      if (menu.menuType && menu.menuType.trim() !== '') {
        converted.meta.menuType = menu.menuType;
      }

      // 处理子菜单 - 传递当前路由的祖先名称列表，用于检测重复
      if (menu.children && Array.isArray(menu.children) && menu.children.length > 0) {
        converted.children = convertMenuData(menu.children, currentParentNames, allNames);

        if (converted.children.length === 0 && !converted.component) {
          converted.component = '';
        }
      }

      return converted;
    })
    .filter((item) => item !== null && item !== undefined);

  return result;
}
