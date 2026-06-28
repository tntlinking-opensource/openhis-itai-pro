<script lang="ts" setup>
/** sv-print 对齐工具栏（工具定义摘自 sv-print toolbar.svelte） */
export type AlignType =
  | 'bottom'
  | 'distributeHor'
  | 'distributeVer'
  | 'horizontal'
  | 'left'
  | 'right'
  | 'top'
  | 'vertical';

defineOptions({ name: 'AlignToolbar' });

withDefaults(defineProps<Props>(), {
  hasSelection: false,
  multiSelection: false,
});

const emit = defineEmits<{
  align: [type: AlignType];
}>();

interface Props {
  hasSelection?: boolean;
  multiSelection?: boolean;
}

const alignTools: { icon: string; key: AlignType; needMulti?: boolean; title: string }[] = [
  { key: 'left', icon: 'sv-left', title: '左对齐' },
  { key: 'vertical', icon: 'sv-vertical', title: '水平居中' },
  { key: 'right', icon: 'sv-right', title: '右对齐' },
  { key: 'top', icon: 'sv-top', title: '顶部对齐' },
  { key: 'horizontal', icon: 'sv-horizontal', title: '垂直居中' },
  { key: 'bottom', icon: 'sv-bottom', title: '底部对齐' },
  { key: 'distributeHor', icon: 'sv-distributeHor', title: '水平分散', needMulti: true },
  { key: 'distributeVer', icon: 'sv-distributeVer', title: '垂直分散', needMulti: true },
];

function isDisabled(
  tool: (typeof alignTools)[number],
  hasSelection: boolean,
  multiSelection: boolean,
): boolean {
  if (tool.needMulti) {
    return !multiSelection;
  }
  return !hasSelection;
}
</script>

<template>
  <div class="align-toolbar">
    <template v-for="(tool, index) in alignTools" :key="tool.key">
      <span v-if="index === 6" class="tool-gap"></span>
      <button
        class="tool"
        :class="{ disabled: isDisabled(tool, hasSelection, multiSelection) }"
        :disabled="isDisabled(tool, hasSelection, multiSelection)"
        :title="tool.title"
        type="button"
        @click="emit('align', tool.key)"
      >
        <i class="svicon" :class="tool.icon"></i>
      </button>
    </template>
  </div>
</template>

<style scoped>
.align-toolbar {
  display: flex;
  gap: 2px;
  align-items: center;
}
</style>
