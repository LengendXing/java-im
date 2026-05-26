// Packet header constants matching server Cmd.java
export const MAGIC = 0x4d49;
export const VERSION = 0x0001;
export const HEADER_LEN = 22;

// Command codes
export const Cmd = {
  AUTH: 0x0001,
  AUTH_ACK: 0x0002,
  HEARTBEAT: 0x0003,
  HEARTBEAT_ACK: 0x0004,
  C2C_MSG: 0x0101,
  C2C_MSG_ACK: 0x0102,
  C2C_MSG_NOTIFY: 0x0103,
  GROUP_MSG: 0x0201,
  GROUP_MSG_ACK: 0x0202,
  GROUP_MSG_NOTIFY: 0x0203,
  MSG_ACK: 0x0110,
  SYNC: 0x0301,
  SYNC_ACK: 0x0302,
  SESSION_LIST: 0x0401,
  SESSION_LIST_ACK: 0x0402,
  FRIEND_APPLY: 0x0501,
  FRIEND_LIST: 0x0502,
  GROUP_CREATE: 0x0601,
  GROUP_MEMBER_LIST: 0x0602,
  KICKOFF: 0x0f01,
} as const;

// Message type
export const MsgType = {
  REQUEST: 0,
  RESPONSE: 1,
  NOTIFY: 2,
} as const;

// Content type
export const ContentType = {
  TEXT: 1,
  IMAGE: 2,
  FILE: 3,
  AUDIO: 4,
  VIDEO: 5,
  SYSTEM: 99,
} as const;

// Session type
export const SessionType = {
  C2C: 1,
  GROUP: 2,
} as const;
