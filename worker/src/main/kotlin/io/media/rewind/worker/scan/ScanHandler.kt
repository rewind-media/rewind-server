package io.media.rewind.worker.scan

import com.media.rewind.model.LibraryType
import io.media.rewind.ScanJobHandler
import io.media.rewind.database.Database

fun mkScanJobHandler(db: Database): ScanJobHandler = {context ->
    val lib = context.request
    when(lib.type) {
        LibraryType.show -> ShowScanner(lib, db).scan()
        LibraryType.movie -> TODO()
        LibraryType.image -> TODO()
    }
}