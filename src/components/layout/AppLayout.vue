<template>
  <el-container class="app-shell">
    <Sidebar />
    <el-container class="main-shell">
      <Topbar />
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import Sidebar from './Sidebar.vue'
import Topbar from './Topbar.vue'
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: var(--page-bg);
}

.main-shell {
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.app-main {
  flex: 1;
  padding: 18px 20px 22px;
  overflow-x: hidden;
}
</style>
