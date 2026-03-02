package com.infosung.atomic.app.storage

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

/** Repository for uploaded image metadata. */
interface ImageRepository : JpaRepository<ImageEntity, UUID>
