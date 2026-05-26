<template>
  <div class="h-screen flex flex-col bg-gray-950 text-white">
    <HeaderBar @logout="handleLogout" />
    <div class="flex flex-1 overflow-hidden">
      <SessionList class="w-80 border-r border-gray-800 flex-shrink-0" />
      <div class="flex-1 flex flex-col">
        <div v-if="chat.currentSession" class="flex-1 flex flex-col">
          <div class="px-4 py-3 border-b border-gray-800 bg-gray-900">
            <h2 class="font-semibold text-lg">{{ chat.currentSession?.name }}</h2>
          </div>
          <MessageArea class="flex-1 overflow-y-auto" />
          <MessageInput @send="handleSend" />
        </div>
        <div v-else class="flex-1 flex items-center justify-center text-gray-600">
          <p>Select a conversation to start chatting</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'
import HeaderBar from '../components/HeaderBar.vue'
import SessionList from '../components/SessionList.vue'
import MessageArea from '../components/MessageArea.vue'
import MessageInput from '../components/MessageInput.vue'

const router = useRouter()
const auth = useAuthStore()
const chat = useChatStore()

onMounted(() => {
  if (!auth.isLoggedIn) {
    router.push('/login')
    return
  }
  chat.initListeners()
  chat.requestSessions()
})

function handleSend(text: string) {
  const session = chat.currentSession
  if (!session) return
  if (session.type === 1) {
    chat.sendC2CMessage(session.targetId, text)
  } else if (session.type === 2) {
    chat.sendGroupMessage(session.targetId, text)
  }
}

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>
