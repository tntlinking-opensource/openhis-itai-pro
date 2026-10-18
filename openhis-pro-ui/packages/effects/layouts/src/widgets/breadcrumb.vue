<script lang="ts" setup>
import type { BreadcrumbStyleType } from '@zxplat/types';

import type { IBreadcrumb } from '@zxplat-core/shadcn-ui';

import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { $t } from '@zxplat/locales';

import { VbenBreadcrumbView } from '@zxplat-core/shadcn-ui';

interface Props {
  hideWhenOnlyOne?: boolean;
  showHome?: boolean;
  showIcon?: boolean;
  type?: BreadcrumbStyleType;
}

const props = withDefaults(defineProps<Props>(), {
  showHome: false,
  showIcon: false,
  type: 'normal',
});

const route = useRoute();
const router = useRouter();

const breadcrumbs = computed((): IBreadcrumb[] => {
  const matched = route.matched;

  const resultBreadcrumb: IBreadcrumb[] = [];

  // 如果当前路由有 activePath，尝试查找父路由并添加到面包屑
  const activePath = route.meta?.activePath as string | undefined;
  if (activePath) {
    try {
      const parentRoute = router.resolve(activePath);
      if (parentRoute && parentRoute.matched.length > 0) {
        // 添加父路由到面包屑（排除最后一个，因为它是当前激活的路由）
        for (let i = 0; i < parentRoute.matched.length - 1; i++) {
          const match = parentRoute.matched[i];
          if (!match) {
            continue;
          }
          const { meta, path } = match;
          const component = 'component' in match ? match.component : undefined;
          const { hideChildrenInMenu, hideInBreadcrumb, icon, name, title } = meta || {};
          if (hideInBreadcrumb || hideChildrenInMenu || !path) {
            continue;
          }

          let hasValidComponent = false;
          if (component) {
            if (typeof component === 'object') {
              const componentName =
                (component as any)?.__name ||
                (component as any)?.name ||
                (component as any)?.displayName;
              const isLayoutComponent =
                componentName === 'BasicLayout' ||
                componentName === 'AuthPageLayout' ||
                componentName === 'IFrameView';
              hasValidComponent = !isLayoutComponent;
            } else if (typeof component === 'function') {
              const componentName = (component as any)?.name;
              const isLayoutComponent =
                componentName === 'BasicLayout' ||
                componentName === 'AuthPageLayout' ||
                componentName === 'IFrameView';
              hasValidComponent = !isLayoutComponent;
            }
          }
          const hasChildren = match.children && match.children.length > 0;
          // 对于有有效组件的路由，设置可点击路径
          const breadcrumbPath = hasValidComponent && !hasChildren ? path : undefined;

          resultBreadcrumb.push({
            icon,
            path: breadcrumbPath,
            title: title ? $t((title || name) as string) : '',
          });
        }
        // 添加激活的父路由（最后一个匹配的路由）
        const lastMatch = parentRoute.matched[parentRoute.matched.length - 1];
        if (lastMatch) {
          const { meta, path } = lastMatch;
          const { hideChildrenInMenu, hideInBreadcrumb, icon, name, title } = meta || {};
          if (!hideInBreadcrumb && !hideChildrenInMenu && path) {
            // 对于通过 activePath 指定的父路由，直接使用 activePath 作为可点击路径
            // 这样即使父路由有 children，也可以点击跳转
            resultBreadcrumb.push({
              icon,
              path: activePath,
              title: title ? $t((title || name) as string) : '',
            });
          }
        }
      }
    } catch (error) {
      // 如果解析失败，忽略错误，继续使用 matched
      console.warn('Failed to resolve parent route:', error);
    }
  }

  for (const match of matched) {
    const { meta, path } = match;
    const component = 'component' in match ? match.component : undefined;
    const { hideChildrenInMenu, hideInBreadcrumb, icon, name, title } = meta || {};
    if (hideInBreadcrumb || hideChildrenInMenu || !path) {
      continue;
    }

    // 如果路由没有 component 或者是布局组件（BasicLayout），则不设置 path，使其不可点击
    let hasValidComponent = false;
    if (component) {
      if (typeof component === 'object') {
        // 检查 component 的名称，如果是布局组件则不可点击
        const componentName =
          (component as any)?.__name || (component as any)?.name || (component as any)?.displayName;
        // BasicLayout、AuthPageLayout 等布局组件不可点击
        const isLayoutComponent =
          componentName === 'BasicLayout' ||
          componentName === 'AuthPageLayout' ||
          componentName === 'IFrameView';
        hasValidComponent = !isLayoutComponent;
      } else if (typeof component === 'function') {
        // 对于异步组件，尝试检查其名称
        const componentName = (component as any)?.name;
        const isLayoutComponent =
          componentName === 'BasicLayout' ||
          componentName === 'AuthPageLayout' ||
          componentName === 'IFrameView';
        hasValidComponent = !isLayoutComponent;
      }
    }
    // 如果路由有 children 且没有有效的 component，通常也是父级容器，不可点击
    const hasChildren = match.children && match.children.length > 0;
    const breadcrumbPath = hasValidComponent && !hasChildren ? path || route.path : undefined;

    resultBreadcrumb.push({
      icon,
      path: breadcrumbPath,
      title: title ? $t((title || name) as string) : '',
    });
  }
  if (props.showHome) {
    resultBreadcrumb.unshift({
      icon: 'mdi:home-outline',
      isHome: true,
      path: '/',
    });
  }
  if (props.hideWhenOnlyOne && resultBreadcrumb.length === 1) {
    return [];
  }

  return resultBreadcrumb;
});

function handleSelect(path: string) {
  router.push(path);
}
</script>
<template>
  <VbenBreadcrumbView
    :breadcrumbs="breadcrumbs"
    :show-icon="showIcon"
    :style-type="type"
    class="ml-2"
    @select="handleSelect"
  />
</template>
