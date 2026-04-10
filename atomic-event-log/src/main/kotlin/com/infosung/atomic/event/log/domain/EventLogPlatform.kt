package com.infosung.atomic.event.log.domain
/** Supported platform profiles for the shared event-log envelope. */
enum class EventLogPlatform {
  API,
  WEBSOCKET,
  CLIENT_WEB,
  CLIENT_MOBILE,
  CLIENT_TABLET,
  CLIENT_IPAD,
  CLIENT_DESKTOP,
  SERVER,
  ;

  fun isClientPlatform(): Boolean =
      this == CLIENT_WEB ||
          this == CLIENT_MOBILE ||
          this == CLIENT_TABLET ||
          this == CLIENT_IPAD ||
          this == CLIENT_DESKTOP
}

/** Supported event families across all platforms. */
enum class EventLogEventType {
  REQUEST,
  RESPONSE,
  MESSAGE,
  ACTION,
  LIFECYCLE,
  ERROR,
  SYSTEM,
  AUDIT,
}
