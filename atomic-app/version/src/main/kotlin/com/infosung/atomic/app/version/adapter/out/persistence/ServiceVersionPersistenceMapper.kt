package com.infosung.atomic.app.version.adapter.out.persistence

import com.infosung.atomic.app.version.ServiceVersionEntity
import com.infosung.atomic.app.version.domain.SemanticVersion
import com.infosung.atomic.app.version.domain.VersionPolicy

internal object ServiceVersionPersistenceMapper {
  fun toDomain(entity: ServiceVersionEntity): VersionPolicy {
    return VersionPolicy(
        service = entity.service,
        platform = entity.platform,
        version =
            SemanticVersion(
                major = entity.mainVersion,
                minor = entity.minorVersion,
                patch = entity.patchNumber,
            ),
        requireUpdate = entity.requireUpdate,
        storeAvailable = entity.storeAvailable,
        storeUrl = entity.storeUrl,
    )
  }
}
