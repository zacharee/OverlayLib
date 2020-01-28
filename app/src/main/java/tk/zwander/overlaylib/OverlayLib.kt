package tk.zwander.overlaylib

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import com.android.apksig.ApkSigner
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.ArrayList

@SuppressLint("SetWorldReadable", "SetWorldWritable")
fun Context.compileOverlay(manifest: File, overlayFile: File, resFile: File, targetPackage: String) {
    if (overlayFile.exists()) {
        overlayFile.delete()
    }

    val aaptCmd = StringBuilder()
        .append(aapt)
        .append(" p")
        .append(" -M ")
        .append(manifest)
        .append(" -I ")
        .append("/system/framework/framework-res.apk")
        .apply {
            if (targetPackage != "android" && targetPackage != "fwk") {
                append(" -I ")
                append(packageManager.getApplicationInfo(targetPackage, 0).sourceDir)
            }
        }
        .append(" -S ")
        .append(resFile)
        .append(" -F ")
        .append(overlayFile)
        .toString()

    loggedSh(aaptCmd, shouldThrow = true)

    overlayFile.setExecutable(true, false)
    overlayFile.setReadable(true, false)
    overlayFile.setWritable(true, false)
}

fun Context.alignOverlay(overlayFile: File, alignedOverlayFile: File) {
    if (alignedOverlayFile.exists()) alignedOverlayFile.delete()

    val zipalignCmd = StringBuilder()
        .append(zipalign)
        .append(" 4 ")
        .append(overlayFile.absolutePath)
        .append(" ")
        .append(alignedOverlayFile.absolutePath)
        .toString()

    loggedSh(zipalignCmd, shouldThrow = true)
    loggedSh("chmod 777 ${alignedOverlayFile.absolutePath}")
}

fun Context.signOverlay(overlayFile: File, signed: File) {
    loggedSh("chmod 777 ${overlayFile.absolutePath}")

    val key = File(cacheDir, "/signing-key-new")
    val pass = "overlay".toCharArray()

    if (key.exists()) key.delete()

    val store = KeyStore.getInstance(KeyStore.getDefaultType())
    store.load(assets.open("signing-key-new"), pass)

    val privKey = store.getKey("key", pass) as PrivateKey
    val certs = ArrayList<X509Certificate>()

    certs.add(store.getCertificateChain("key")[0] as X509Certificate)

    val signConfig = ApkSigner.SignerConfig.Builder("overlay", privKey, certs).build()
    val signConfigs = ArrayList<ApkSigner.SignerConfig>()

    signConfigs.add(signConfig)

    val signer = ApkSigner.Builder(signConfigs)
    signer.setV1SigningEnabled(true)
        .setV2SigningEnabled(true)
        .setInputApk(overlayFile)
        .setOutputApk(signed)
        .setMinSdkVersion(Build.VERSION.SDK_INT)
        .build()
        .sign()

    loggedSh("chmod 777 ${signed.absolutePath}")
}

fun Context.doCompileAlignAndSign(
    targetPackage: String,
    suffix: String,
    listener: ((apk: File) -> Unit)? = null,
    resFiles: List<ResourceFileData>
) {
    val base = makeBaseDir(suffix)
    val manifest = getManifest(base, targetPackage)
    val unsignedUnaligned = makeOverlayFile(base, suffix, OverlayType.UNSIGNED_UNALIGNED)
    val unsigned = makeOverlayFile(base, suffix, OverlayType.UNSIGNED)
    val signed = makeOverlayFile(base, suffix, OverlayType.SIGNED)
    val resDir = makeResDir(base)

    resFiles.forEach {
        val dir = File(resDir, it.fileDirectory)

        dir.mkdirs()
        dir.mkdir()

        val resFile = File(dir, it.filename)
        if (resFile.exists()) resFile.delete()

        if (it is ResourceImageData) {
            it.image?.let {
                FileOutputStream(resFile).use { stream ->
                    it.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
            }
        } else {
            OutputStreamWriter(resFile.outputStream()).use { writer ->
                writer.write(it.contents ?: "")
                writer.write("\n")
            }
        }
    }

    compileOverlay(manifest, unsignedUnaligned, resDir, targetPackage)
    alignOverlay(unsignedUnaligned, unsigned)
    signOverlay(unsigned, signed)

    Shell.sh("cp ${signed.absolutePath} ${signed.absolutePath}").submit {
        handleShellResult(it, true)
        listener?.invoke(signed)
    }
}
