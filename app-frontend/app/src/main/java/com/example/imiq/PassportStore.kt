package com.example.imiq

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Stores the current Cognitive Passport plus an append-only history of every
 * validated revision. Saving a new revision never destroys the previous one.
 *
 * `cognitive_passport.json` remains the compatibility pointer to the latest
 * revision used by routing/UI. Immutable snapshots live under
 * `cognitive_passport_history/` and can later support longitudinal analysis.
 */
object PassportStore {
    private const val CURRENT_FILENAME = "cognitive_passport.json"
    private const val HISTORY_DIRNAME = "cognitive_passport_history"
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun currentFile(): File {
        val ctx = appContext ?: error("PassportStore.init(context) must be called first")
        return File(ctx.filesDir, CURRENT_FILENAME)
    }

    private fun historyDir(): File {
        val ctx = appContext ?: error("PassportStore.init(context) must be called first")
        return File(ctx.filesDir, HISTORY_DIRNAME).apply { mkdirs() }
    }

    private data class Identity(val passportId: String?, val revision: Int?)

    private fun identity(passportJson: String): Identity = runCatching {
        val cp = Json.parseToJsonElement(passportJson)
            .jsonObject["cognitive_passport"]?.jsonObject
            ?: return@runCatching Identity(null, null)
        val lineage = cp["lineage"]?.jsonObject
        Identity(
            passportId = lineage?.get("passport_id")?.jsonPrimitive?.contentOrNull,
            revision = lineage?.get("revision")?.jsonPrimitive?.intOrNull,
        )
    }.getOrElse { Identity(null, null) }

    private fun archive(passportJson: String) {
        val id = identity(passportJson)
        val safeId = id.passportId
            ?.replace(Regex("[^A-Za-z0-9_-]"), "_")
            ?.take(64)
            ?: "legacy_${passageFingerprint(passportJson)}"
        val revision = id.revision?.toString()?.padStart(6, '0') ?: "unknown"
        val target = File(historyDir(), "revision_${revision}_$safeId.json")
        if (!target.exists()) {
            target.writeText(passportJson, Charsets.UTF_8)
        } else if (target.readText(Charsets.UTF_8) != passportJson) {
            error("Passport history collision for revision=$revision id=$safeId")
        }
    }

    private fun passageFingerprint(value: String): String =
        value.hashCode().toUInt().toString(16)

    /**
     * Save one validated Passport as the current revision and archive it.
     * If an older compatibility file exists (including a pre-lineage file), it
     * is archived before the current pointer changes.
     */
    fun save(passportJson: String) {
        val current = currentFile()
        if (current.exists()) {
            val previous = current.readText(Charsets.UTF_8)
            if (previous != passportJson) archive(previous)
        }
        archive(passportJson)
        current.writeText(passportJson, Charsets.UTF_8)
    }

    /** Load the latest Passport JSON, or null if none is current. */
    fun load(): String? {
        val f = currentFile()
        return if (f.exists()) f.readText(Charsets.UTF_8) else null
    }

    /** True if this device has a current Passport. */
    fun hasPassport(): Boolean = currentFile().exists()

    /**
     * Immutable Passport snapshots ordered by revision when available.
     * The full JSON is returned intentionally so longitudinal analyses retain
     * needs, beliefs, valences, XAI and deliberation diagnostics at each wave.
     */
    fun history(): List<String> = historyDir()
        .listFiles { file -> file.isFile && file.extension == "json" }
        .orEmpty()
        .sortedBy { it.name }
        .map { it.readText(Charsets.UTF_8) }

    fun historyCount(): Int = historyDir()
        .listFiles { file -> file.isFile && file.extension == "json" }
        ?.size ?: 0

    /**
     * Start a fresh full calibration without deleting longitudinal evidence.
     * The current snapshot is archived, then only the compatibility pointer is
     * removed. The next generated Passport becomes the new current snapshot.
     */
    fun clearCurrentPreservingHistory() {
        val current = currentFile()
        if (current.exists()) {
            archive(current.readText(Charsets.UTF_8))
            current.delete()
        }
    }

    /**
     * Explicit destructive reset for a future privacy/delete-data action.
     * Normal updates and normal recalibration must not call this.
     */
    fun clear() {
        val ctx = appContext ?: error("PassportStore.init(context) must be called first")
        clearPassportFiles(ctx.filesDir)
    }

    internal fun clearPassportFiles(filesDir: File) {
        val current = File(filesDir, CURRENT_FILENAME)
        if (current.exists()) current.delete()
        val history = File(filesDir, HISTORY_DIRNAME)
        history.listFiles()?.forEach { it.delete() }
        history.delete()
    }
}
