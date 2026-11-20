package io.github.smicca.lector.core.storage

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import io.github.smicca.lector.core.model.CatalogEntry
import io.github.smicca.lector.core.model.InstalledModel
import io.github.smicca.lector.core.model.VoiceModelCatalog
import io.github.smicca.lector.core.model.VoiceOption
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object ModelStorage {

    fun getModelsDir(context: Context): File {
        val candidates = listOfNotNull(
            File(context.filesDir, "models"),
            context.getExternalFilesDir(null)?.let { File(it, "models") },
            File("/sdcard/Android/data/io.github.smicca.lector.debug/files/models"),
            File("/sdcard/Android/data/io.github.smicca.lector/files/models")
        )
        val existing = candidates.firstOrNull { it.exists() && it.listFiles()?.any { f -> f.isDirectory } == true }
        if (existing != null) return existing

        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getAvailableStorageBytes(context: Context): Long {
        return try {
            val dir = getModelsDir(context)
            val stat = StatFs(dir.absolutePath)
            stat.availableBytes
        } catch (e: Exception) {
            0L
        }
    }

    fun calculateDirSize(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun verifyFileSha256(file: File, expectedSha256: String): Boolean {
        if (!file.exists() || !file.isFile) return false
        val computed = computeSha256(file)
        return computed.equals(expectedSha256.trim(), ignoreCase = true)
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(65536)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun detectModelType(folder: File): String? {
        val files = folder.walkTopDown().maxDepth(3).toList()
        val fileNames = files.map { it.name.lowercase() }

        val hasVoicesBin = fileNames.any { it == "voices.bin" }
        val onnxFiles = fileNames.filter { it.endsWith(".onnx") }

        if (hasVoicesBin && onnxFiles.isNotEmpty()) {
            val isKitten = folder.name.lowercase().contains("kitten") ||
                    fileNames.any { it.contains("kitten") }
            return if (isKitten) "KITTEN" else "KOKORO"
        }

        if (fileNames.any { it.contains("duration_predictor") } &&
            fileNames.any { it.contains("text_encoder") }
        ) {
            return "SUPERTONIC"
        }

        if (fileNames.any { it.contains("lm_flow") } &&
            fileNames.any { it.contains("lm_main") }
        ) {
            return "POCKET"
        }

        if (onnxFiles.isNotEmpty() && fileNames.any { it == "tokens.txt" }) {
            return "PIPER_VITS"
        }

        if (onnxFiles.isNotEmpty()) {
            return "PIPER_VITS"
        }

        return null
    }

    fun getInstalledModels(context: Context, selectedId: String?): List<InstalledModel> {
        val modelsDir = getModelsDir(context)
        val catalog = VoiceModelCatalog.loadCatalog(context)
        val catalogByFolder = catalog.associateBy { it.folderName }
        val catalogById = catalog.associateBy { it.id }

        val subdirs = modelsDir.listFiles { f -> f.isDirectory } ?: emptyArray()
        android.util.Log.i("ModelStorage", "modelsDir: ${modelsDir.absolutePath}, subdirs: ${subdirs.map { it.name }}")
        val installedList = mutableListOf<InstalledModel>()

        for (dir in subdirs) {
            val detectedType = detectModelType(dir) ?: continue
            val size = calculateDirSize(dir)
            val catalogMatch = catalogByFolder[dir.name] ?: catalogById[dir.name]

            val (id, name, voices) = if (catalogMatch != null) {
                Triple(catalogMatch.id, catalogMatch.name, catalogMatch.voices)
            } else {
                val defaultVoices = if (detectedType == "KOKORO") {
                    listOf(
                        VoiceOption("en-us-michael", "Michael", 16, "en-US", isDefault = true),
                        VoiceOption("en-us-heart", "Heart", 3, "en-US"),
                        VoiceOption("en-gb-george", "George", 26, "en-GB"),
                        VoiceOption("en-gb-emma", "Emma", 21, "en-GB")
                    )
                } else {
                    listOf(VoiceOption("default", "Default", 0, "en-US", isDefault = true))
                }
                Triple(dir.name, dir.name.replace('-', ' ').replace('_', ' ').capitalizeWords(), defaultVoices)
            }

            installedList.add(
                InstalledModel(
                    id = id,
                    name = name,
                    folderName = dir.name,
                    modelType = detectedType,
                    sizeBytes = size,
                    voices = voices,
                    isSelected = (id == selectedId || (selectedId == null && catalogMatch?.recommended == true))
                )
            )
        }

        return installedList.sortedWith(
            compareByDescending<InstalledModel> { it.isSelected }
                .thenBy { it.name }
        )
    }

    fun deleteModel(context: Context, folderName: String): Boolean {
        val modelsDir = getModelsDir(context)
        val target = File(modelsDir, folderName)
        if (!target.exists()) return false
        return target.deleteRecursively()
    }

    fun extractArchive(
        archiveFile: File,
        destDir: File,
        onProgress: ((Int) -> Unit)? = null
    ) {
        destDir.mkdirs()
        val name = archiveFile.name.lowercase()
        if (name.endsWith(".tar.bz2") || name.endsWith(".tbz2") || name.endsWith(".tar")) {
            extractTarBz2(archiveFile, destDir, onProgress)
        } else if (name.endsWith(".zip")) {
            extractZip(archiveFile, destDir, onProgress)
        } else {
            throw IllegalArgumentException("Unsupported archive format: ${archiveFile.name}")
        }
    }

    fun extractTarBz2(
        tarBz2File: File,
        destDir: File,
        onProgress: ((Int) -> Unit)? = null
    ) {
        val totalBytes = tarBz2File.length()
        var readBytes = 0L

        FileInputStream(tarBz2File).use { fis ->
            BufferedInputStream(fis).use { bis ->
                val bzIn: InputStream = if (tarBz2File.name.endsWith(".tar")) bis else BZip2CompressorInputStream(bis)
                TarArchiveInputStream(bzIn).use { tarIn ->
                    var entry = tarIn.nextEntry
                    while (entry != null) {
                        val outFile = File(destDir, entry.name)
                        // Canonical path check to prevent path traversal
                        if (!outFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                            throw SecurityException("Invalid zip/tar entry: ${entry.name}")
                        }

                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                tarIn.copyTo(fos)
                            }
                        }

                        readBytes += entry.size
                        if (totalBytes > 0 && onProgress != null) {
                            val percent = ((readBytes.toDouble() / (totalBytes * 2.5)) * 100).toInt().coerceIn(0, 100)
                            onProgress(percent)
                        }
                        entry = tarIn.nextEntry
                    }
                }
            }
        }
        onProgress?.invoke(100)
    }

    fun extractZip(
        zipFile: File,
        destDir: File,
        onProgress: ((Int) -> Unit)? = null
    ) {
        val totalBytes = zipFile.length()
        var readBytes = 0L

        FileInputStream(zipFile).use { fis ->
            BufferedInputStream(fis).use { bis ->
                ZipInputStream(bis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(destDir, entry.name)
                        if (!outFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                            throw SecurityException("Invalid zip entry: ${entry.name}")
                        }

                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }

                        readBytes += entry.size
                        if (totalBytes > 0 && onProgress != null) {
                            val percent = ((readBytes.toDouble() / (totalBytes * 2.0)) * 100).toInt().coerceIn(0, 100)
                            onProgress(percent)
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        }
        onProgress?.invoke(100)
    }

    fun importFromUri(context: Context, uri: Uri): Result<InstalledModel> {
        return try {
            val fileName = queryFileName(context, uri) ?: "imported_model.tar.bz2"
            val tempArchive = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}_$fileName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempArchive).use { output ->
                    input.copyTo(output)
                }
            } ?: return Result.failure(IllegalStateException("Could not read document from URI"))

            val tempExtractDir = File(context.cacheDir, "extract_${System.currentTimeMillis()}")
            tempExtractDir.mkdirs()

            try {
                extractArchive(tempArchive, tempExtractDir)
            } finally {
                tempArchive.delete()
            }

            // Check if extracted contents are wrapped in a single top-level folder
            val extractedChildren = tempExtractDir.listFiles() ?: emptyArray()
            val effectiveDir = if (extractedChildren.size == 1 && extractedChildren[0].isDirectory) {
                extractedChildren[0]
            } else {
                tempExtractDir
            }

            val modelType = detectModelType(effectiveDir)
                ?: return Result.failure(IllegalArgumentException("Unrecognized TTS model layout. Model directory must contain model weights (.onnx) and configuration/lexicon files."))

            val baseName = fileName.substringBefore('.').trim()
            val targetFolder = File(getModelsDir(context), baseName)
            if (targetFolder.exists()) {
                targetFolder.deleteRecursively()
            }

            if (effectiveDir != tempExtractDir) {
                effectiveDir.renameTo(targetFolder)
                tempExtractDir.deleteRecursively()
            } else {
                tempExtractDir.renameTo(targetFolder)
            }

            val size = calculateDirSize(targetFolder)
            val defaultVoice = VoiceOption("default", baseName, 0, "en-US", isDefault = true)
            val installed = InstalledModel(
                id = baseName,
                name = baseName.replace('-', ' ').replace('_', ' ').capitalizeWords(),
                folderName = targetFolder.name,
                modelType = modelType,
                sizeBytes = size,
                voices = listOf(defaultVoice),
                isSelected = true
            )

            Result.success(installed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) result = cursor.getString(idx)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
