<!--
  表格单元格内「点击弹出」选列表容器（药房单据遗留用法）。
  展开时向父组件 @search 当前关键字，供下拉表格分页检索。
-->
<script setup>
import { ref, watch } from 'vue';

defineOptions({
  name: 'OpenHisPopoverList',
});

const props = defineProps({
  /** 当前单元格展示文案（品名等），同时也是检索关键字初始值 */
  modelValue: {
    type: String,
    default: '',
  },
  /** Popover 宽度（px） */
  width: {
    type: Number,
    default: 800,
  },
});

const emit = defineEmits(['search']);

const keyword = ref(props.modelValue ?? '');

watch(
  () => props.modelValue,
  (v) => {
    keyword.value = v ?? '';
  },
);

function emitSearch() {
  emit('search', keyword.value ?? '');
}

function onPopoverShow() {
  keyword.value = props.modelValue ?? '';
  emitSearch();
}

function onInput(val) {
  keyword.value = val;
  emitSearch();
}
</script>

<template>
  <el-popover trigger="click" placement="bottom-start" :width="width" @show="onPopoverShow">
    <template #reference>
      <el-input
        v-model="keyword"
        clearable
        placeholder="点击选择"
        @input="onInput"
        @clear="emitSearch"
      />
    </template>
    <div class="max-h-[min(70vh,560px)] overflow-auto">
      <slot name="popover-content" />
    </div>
  </el-popover>
</template>
