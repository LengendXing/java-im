<template>
  <div class="h-screen flex flex-col bg-gray-950 text-white">
    <div class="px-4 py-3 border-b border-gray-800 bg-gray-900 flex items-center justify-between">
      <button @click="router.push('/')" class="text-gray-400 hover:text-white text-sm">&larr; Back</button>
      <h2 class="font-semibold text-lg">{{ chat.currentSession?.name || '' }}</h2>
      <span class="w-8"></span>
    </div>
    <MessageArea />
    <MessageInput @send="handleSend" @send-file="handleSendFile" />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'
import MessageArea from '../components/chat/MessageArea.vue'
import MessageInput from '../components/chat/MessageInput.vue'

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
  if (session.type === 1) chat.sendC2CMessage(session.targetId, text)
  else if (session.type === 2) chat.sendGroupMessage(session.targetId, text)
}

function handleSendFile(msgType: number, url: string, fileName: string, fileSize: number) {
  const session = chat.currentSession
  if (!session) return
  if (session.type === 1) chat.sendC2CFileMessage(session.targetId, msgType, url, fileName, fileSize)
  else if (session.type === 2) chat.sendGroupFileMessage(session.targetId, msgType, url, fileName, fileSize)
}
</script>
