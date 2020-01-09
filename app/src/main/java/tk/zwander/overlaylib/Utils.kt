package tk.zwander.overlaylib

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import androidx.core.content.pm.PackageInfoCompat
import com.topjohnwu.superuser.Shell
import java.io.*
import javax.crypto.Cipher
import javax.crypto.CipherInputStream

fun initShell() {
    Shell.Config.setFlags(Shell.FLAG_REDIRECT_STDERR)
    Shell.Config.setTimeout(10)
}

val Context.aapt: String?
    get() {
        val aapt = File(cacheDir, "aapt")

        if (!aapt.exists() && !assets.extractAsset("aapt", aapt.absolutePath))
            return null

        aapt.setExecutable(true)
        aapt.setWritable(true)
        aapt.setReadable(true)

        return aapt.absolutePath
    }

val Context.zipalign: String?
    get() {
        val zipalign = File(cacheDir, "zipalign")

        if (!zipalign.exists() && !assets.extractAsset("zipalign", zipalign.absolutePath))
            return null

        zipalign.setExecutable(true)
        zipalign.setWritable(true)
        zipalign.setReadable(true)

        return zipalign.absolutePath
    }

@SuppressLint("SetWorldWritable", "SetWorldReadable")
fun Context.makeBaseDir(suffix: String): File {
    val dir = File(cacheDir, suffix)

    if (dir.exists()) dir.deleteRecursively()

    dir.mkdirs()
    dir.mkdir()

    dir.setExecutable(true, false)
    dir.setReadable(true, false)
    dir.setWritable(true, false)

    return dir
}

fun getManifest(base: File, suffix: String, targetPackage: String, overlayPkg: String = "tk.zwander.overlaylib.$targetPackage"): File {
    val builder = StringBuilder()
    builder.append("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>")
    builder.append(
        "<manifest " +
                "xmlns:android=\"http://schemas.android.com/apk/res/android\" " +
                "package=\"$overlayPkg\" " +
                "android:versionCode=\"100\" " +
                "android:versionName=\"100\"> "
    )
    builder.append("<uses-permission android:name=\"com.samsung.android.permission.SAMSUNG_OVERLAY_COMPONENT\" />")
    builder.append("<overlay android:targetPackage=\"$targetPackage\" />")
    builder.append("</manifest>")

    val manifestFile = File(base, "AndroidManifest.xml")
    if (manifestFile.exists()) manifestFile.delete()

    OutputStreamWriter(manifestFile.outputStream()).use {
        it.write(builder.toString())
        it.write("\n")
    }

    return manifestFile
}

fun AssetManager.extractAsset(assetPath: String, devicePath: String, cipher: Cipher?): Boolean {
    try {
        val files = list(assetPath) ?: emptyArray()
        if (files.isEmpty()) {
            return handleExtractAsset(this, assetPath, devicePath, cipher)
        }
        val f = File(devicePath)
        if (!f.exists() && !f.mkdirs()) {
            throw RuntimeException("cannot create directory: $devicePath")
        }
        var res = true
        for (file in files) {
            val assetList = list("$assetPath/$file") ?: emptyArray()
            res = if (assetList.isEmpty()) {
                res and handleExtractAsset(this, "$assetPath/$file", "$devicePath/$file", cipher)
            } else {
                res and extractAsset("$assetPath/$file", "$devicePath/$file", cipher)
            }
        }
        return res
    } catch (e: IOException) {
        e.printStackTrace()
        return false
    }
}

fun AssetManager.extractAsset(assetPath: String, devicePath: String): Boolean {
    return extractAsset(assetPath, devicePath, null)
}

private fun handleExtractAsset(
    am: AssetManager, assetPath: String, devicePath: String,
    cipher: Cipher?
): Boolean {
    var path = devicePath
    var `in`: InputStream? = null
    var out: OutputStream? = null
    val parent = File(path).parentFile
    if (!parent.exists() && !parent.mkdirs()) {
        throw RuntimeException("cannot create directory: " + parent.absolutePath)
    }

    if (path.endsWith(".enc")) {
        path = path.substring(0, path.lastIndexOf("."))
    }

    try {
        `in` = if (cipher != null && assetPath.endsWith(".enc")) {
            CipherInputStream(am.open(assetPath), cipher)
        } else {
            am.open(assetPath)
        }
        out = FileOutputStream(File(path))
        val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
        var len: Int = `in`!!.read(bytes)
        while (len != -1) {
            out.write(bytes, 0, len)
            len = `in`.read(bytes)
        }
        return true
    } catch (e: IOException) {
        e.printStackTrace()
        return false
    } finally {
        try {
            `in`?.close()
            out?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }
}

fun makeOverlayFile(base: File, suffix: String, type: OverlayType): File {
    return File(base, "${suffix}_$type.apk")
}

fun makeEmptyAnimationList(): String {
    return StringBuilder()
        .append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        .append("<animation-list xmlns:android=\"http://schemas.android.com/apk/res/android\" android:oneshot=\"false\">\n")
        .append("<item android:duration=\"50\" android:drawable=\"@android:color/transparent\" />\n")
        .append("</animation-list>\n")
        .toString()
}

fun makeResourceXml(vararg data: ResourceData): String {
    return StringBuilder()
        .append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        .append("<resources xmlns:android=\"http://schemas.android.com/apk/res/android\">")
        .apply {
            data.forEach {
                append("<item type=\"${it.type}\" ${it.otherData} name=\"${it.name}\">${it.value}</item>")
            }
        }
        .append("</resources>")
        .toString()
}

fun makeResDir(base: File): File {
    val dir = File(base, "res/")
    if (dir.exists()) dir.deleteRecursively()

    dir.mkdirs()
    dir.mkdir()

    return dir
}