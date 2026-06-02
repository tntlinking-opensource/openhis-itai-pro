<script setup lang="ts">
import { computed } from 'vue';

import { Search } from '@element-plus/icons-vue';
import { ElIcon, ElInput } from 'element-plus';

defineOptions({
  name: 'SelectFuzzyInput',
});

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '请输入关键字',
  clearable: true,
  disabled: false,
  fullWidth: false,
});

const emit = defineEmits<{
  search: [value: string];
  'update:modelValue': [value: string];
}>();

interface Props {
  modelValue?: string;
  placeholder?: string;
  clearable?: boolean;
  disabled?: boolean;
  /** 为 true 时占满父容器宽度（侧栏等）；默认 false 保持 220px 以兼容原列表页 */
  fullWidth?: boolean;
}

const inputValue = computed({
  get: () => props.modelValue,
  set: (value: string) => emit('update:modelValue', value),
});

const handleSearch = () => {
  emit('search', inputValue.value ?? '');
};
</script>

<template>
  <ElInput
    v-model="inputValue"
    :placeholder="props.placeholder"
    :clearable="props.clearable"
    :disabled="props.disabled"
    :class="props.fullWidth ? '!w-full' : '!w-[220px]'"
    @keyup.enter="handleSearch"
    @clear="handleSearch"
  >
    <template #suffix>
      <ElIcon class="cursor-pointer text-gray-400" @click="handleSearch">
        <Search />
      </ElIcon>
    </template>
  </ElInput>
</template>
