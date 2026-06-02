<script setup lang="ts">
interface Props {
  title: string;
}

const props = defineProps<Props>();
</script>

<template>
  <div class="section-title text-base font-semibold">
    {{ props.title }}
  </div>
</template>

<style scoped>
.section-title {
  position: relative;
  padding-left: 10px;
  color: #020817;
}

.section-title::before {
  position: absolute;
  top: 50%;
  left: 0;
  width: 3px;
  height: 14px;
  background: var(--el-color-primary);
  border-radius: 2px;
  content: '';
  transform: translateY(-50%);
}
</style>
