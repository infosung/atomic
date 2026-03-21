package com.infosung.atomic.app.storage.application.port.`in`

import com.infosung.atomic.app.storage.application.model.DeleteAppImageCommand

fun interface DeleteAppImageUseCase {
  fun deleteImage(command: DeleteAppImageCommand)
}
