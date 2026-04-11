package com.infosung.atomic.event.log.ingest.api.adapter.out.authorization

import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiRequestMetadata
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeBatch
import com.infosung.atomic.event.log.ingest.api.application.port.out.AuthorizeEventLogIngestRequestPort
import org.slf4j.LoggerFactory

/** Default no-op authorizer for official ingest requests. */
class AllowAllEventLogIngestRequestAuthorizer : AuthorizeEventLogIngestRequestPort {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun authorize(
      batch: EventLogIngestIntakeBatch,
      requestMetadata: EventLogIngestApiRequestMetadata,
  ) {
    log.debug(
        "Event log ingest authorization passed by default authorizer: serviceId={}, uri={}",
        batch.serviceId,
        requestMetadata.requestUri,
    )
  }
}
