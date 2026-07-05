package com.routersync.app.sync

/** Le categorie in cui vengono automaticamente smistati i file durante la sincronizzazione. */
enum class FileCategory(val folderName: String) {
    IMMAGINI("Immagini"),
    VIDEO("Video"),
    AUDIO("Audio"),
    DOCUMENTI("Documenti") // categoria "cassetto": tutto ciò che non rientra nelle altre
}

/** Nome della cartella che contiene il backup esatto (struttura originale) della cartella locale scelta. */
const val ALL_BACKUP_FOLDER_NAME = "All"

/** Rende un nome scelto dall'utente sicuro da usare come nome di cartella (rimuove caratteri non validi). */
fun sanitizeFolderName(name: String): String {
    val cleaned = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
    return cleaned.ifBlank { "Sync" }
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "mkv", "avi", "webm", "3gp", "m4v", "wmv")
private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "wma", "opus")

/** Determina la categoria di destinazione di un file in base alla sua estensione. */
fun categoryForFileName(name: String): FileCategory {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        ext in IMAGE_EXTENSIONS -> FileCategory.IMMAGINI
        ext in VIDEO_EXTENSIONS -> FileCategory.VIDEO
        ext in AUDIO_EXTENSIONS -> FileCategory.AUDIO
        else -> FileCategory.DOCUMENTI
    }
}
