package tk.zwander.overlaylib

import android.graphics.Bitmap

open class ResourceImageData(
     filename: String,
     fileDirectory: String,
     val image: Bitmap?
) : ResourceFileData(
     filename,
     fileDirectory,
     null
)