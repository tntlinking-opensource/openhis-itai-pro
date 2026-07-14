<!--
  库房单据明细表键盘区壳：绑定 gridWrapRef + 方向键导航。
  表格外 mousedown 失焦仍由业务页外层容器绑定 gridEdit.onSurfaceMouseDown。
-->
<script setup lang="ts">
defineOptions({ name: 'InvDocDetailGridShell' });

interface Props {
  bindGridWrap: (el: HTMLElement | null) => void;
  onGridKeydown: (event: KeyboardEvent) => void | Promise<void>;
}

const props = defineProps<Props>();
</script>

<template>
  <div
    :ref="(el) => props.bindGridWrap(el as HTMLElement | null)"
    class="inv-doc-detail-grid-shell mt-2 flex min-h-0 min-w-0 flex-1 flex-col"
    @keydown.capture="props.onGridKeydown"
  >
    <slot />
  </div>
</template>

<style scoped lang="scss">
.inv-doc-detail-grid-shell :deep(.vxe-grid) {
  flex: 1;
  min-height: 0;
}
</style>
