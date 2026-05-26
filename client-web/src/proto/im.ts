// Protobuf message types matching im.proto
export interface UserInfo {
  userId: number
  username: string
  nickname: string
  avatarUrl: string
}

export interface MessageContent {
  msgType: number
  text: string
  url: string
  fileName: string
  fileSize: number
  width: number
  height: number
  duration: number
  extra: string
}

export interface Message {
  msgId: number
  senderId: number
  seq: number
  content: MessageContent
  serverTime: number
}

export interface SessionInfo {
  sessionId: string
  type: number
  targetId: number
  name: string
  avatarUrl: string
  lastMsg: string
  lastMsgTime: number
  unreadCount: number
  isTop: boolean
  isMuted: boolean
}

export interface AuthRequest {
  token: string
  deviceId: string
  platform: number
}

export interface AuthResponse {
  code: number
  msg: string
  userInfo: UserInfo | null
  serverTime: number
}

export interface C2CMsgRequest {
  receiverId: number
  content: MessageContent
  clientMsgId: string
}

export interface C2CMsgResponse {
  code: number
  msg: string
  msgId: number
  seq: number
  serverTime: number
}

export interface C2CMsgNotify {
  msgId: number
  senderId: number
  sessionId: string
  seq: number
  content: MessageContent
  serverTime: number
}

export interface GroupMsgNotify {
  msgId: number
  senderId: number
  groupId: number
  sessionId: string
  seq: number
  content: MessageContent
  atUserIds: number[]
  serverTime: number
}

export interface SyncPoint {
  sessionId: string
  lastSeq: number
}

export interface SessionMessages {
  sessionId: string
  messages: Message[]
  hasMore: boolean
}

export function encodePacket(cmd: number, msgType: number, seqId: number, body: Uint8Array): ArrayBuffer {
  const totalLen = 22 + body.length
  const buf = new ArrayBuffer(totalLen)
  const view = new DataView(buf)
  const arr = new Uint8Array(buf)

  view.setUint16(0, 0x4D49, true)  // magic
  view.setUint16(2, 0x0001, true)  // version
  view.setUint16(4, cmd, true)     // cmd
  view.setUint8(6, msgType)        // msgType
  view.setUint32(7, seqId, true)   // sequenceId
  view.setUint32(11, body.length, true) // dataLength
  // bytes 15-21: padding (7 bytes, already zero)
  arr.set(body, 22)

  return buf
}

export interface DecodedPacket {
  cmd: number
  msgType: number
  sequenceId: number
  body: Uint8Array
}

export function decodePacket(data: ArrayBuffer): DecodedPacket | null {
  const buf = new Uint8Array(data)
  if (buf.length < 22) return null

  const view = new DataView(data)
  const magic = view.getUint16(0, true)
  if (magic !== 0x4D49) return null

  const cmd = view.getUint16(4, true)
  const msgType = view.getUint8(6)
  const sequenceId = view.getUint32(7, true)
  const dataLength = view.getUint32(11, true)

  if (buf.length < 22 + dataLength) return null

  const body = buf.slice(22, 22 + dataLength)
  return { cmd, msgType, sequenceId, body }
}

// JSON-based encoding for protobuf messages (simplified for v0.1)
// Full Protobuf integration uses protobufjs loaded from im.proto
export function encodeJsonBody(obj: Record<string, unknown>): Uint8Array {
  const encoder = new TextEncoder()
  return encoder.encode(JSON.stringify(obj))
}

export function decodeJsonBody<T>(body: Uint8Array): T {
  const decoder = new TextDecoder()
  return JSON.parse(decoder.decode(body)) as T
}
