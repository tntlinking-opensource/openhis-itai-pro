<script setup lang="ts">
interface Props {
  editing?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  editing: false,
});

const emit = defineEmits<{
  add: [];
  cancel: [];
  copy: [];
  delete: [];
  edit: [];
  save: [];
}>();
</script>

<template>
  <div class="timeline-card__row-actions">
    <ElButton link type="primary" @click="emit('add')">新增</ElButton>
    <ElButton link type="primary" @click="emit('copy')">复制</ElButton>
    <ElButton link type="primary" :disabled="props.editing" @click="emit('edit')"> 编辑 </ElButton>
    <ElButton link type="danger" @click="emit('delete')">删除</ElButton>
    <template v-if="props.editing">
      <ElButton type="primary" plain @click="emit('save')">保存</ElButton>
      <ElButton plain @click="emit('cancel')">取消</ElButton>
    </template>
  </div>
</template>

<style scoped>
.timeline-card__row-actions {
  display: inline-flex;
  align-items: center;
  margin-left: auto;
  justify-content: flex-end;
  gap: 2px;
}
</style>
