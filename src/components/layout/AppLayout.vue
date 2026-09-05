<template>
  <div class="shell">
    <Sidebar />
    <main class="main">
      <Topbar />
      <div class="content">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup>
import Sidebar from './Sidebar.vue'
import Topbar from './Topbar.vue'
</script>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
  background: var(--bg);
}

.main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.content {
  flex: 1;
  padding: 22px 24px 40px;
  overflow-x: hidden;
}
</style>
