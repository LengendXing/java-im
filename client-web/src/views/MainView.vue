<template>
  <div class="h-screen flex flex-col bg-[var(--im-bg)] text-[var(--im-text)]">
    <div class="flex-1 overflow-hidden">
      <ChatPage v-if="activeTab === 'chat'" />
      <ContactsPage v-if="activeTab === 'contacts'" />
      <ProfilePage v-if="activeTab === 'profile'" />
    </div>
    <nav class="im-tab-bar">
      <div
        v-for="tab in tabs" :key="tab.key"
        class="im-tab-item"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        <span class="text-xl">{{ tab.icon }}</span>
        <span class="text-[10px]">{{ $t(`${tab.key}.title`) }}</span>
      </div>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'
import ChatPage from '../views/tabs/ChatPage.vue'
import ContactsPage from '../views/tabs/ContactsPage.vue'
import ProfilePage from '../views/tabs/ProfilePage.vue'

const router = useRouter()
const auth = useAuthStore()
const chat = useChatStore()

const activeTab = ref<'chat' | 'contacts' | 'profile'>('chat')

const tabs = [
  { key: 'chat' as const, icon: '💬' },
  { key: 'contacts' as const, icon: '👤' },
  { key: 'profile' as const, icon: '⚙' },
]

onMounted(() => {
  if (!auth.isLoggedIn) {
    router.push('/login')
    return
  }
  chat.initListeners()
  chat.requestSessions()
})
</script>
