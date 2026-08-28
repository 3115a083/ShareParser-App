package cc.stkmn.shareparser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.io.File

class SaveTextFileActivity : ComponentActivity() {
    private var sourcePath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourcePath = intent.getStringExtra(EXTRA_SOURCE_PATH).orEmpty()
        val source = File(sourcePath)
        if (!source.isFile) {
            finish()
            return
        }

        val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE).orEmpty().ifBlank { "text/plain" }
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty().ifBlank { "ShareParser.txt" }
        runCatching {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mimeType
                    putExtra(Intent.EXTRA_TITLE, fileName)
                },
                REQUEST_CREATE_DOCUMENT
            )
        }.onFailure {
            Toast.makeText(this, "Speicherort konnte nicht geöffnet werden.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Deprecated in Android; retained for the one-shot document picker on API 26+.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CREATE_DOCUMENT) return
        if (resultCode == Activity.RESULT_OK) {
            val target = data?.data
            if (target != null) {
                runCatching {
                    contentResolver.openOutputStream(target, "w")?.use { output ->
                        File(sourcePath).inputStream().use { input -> input.copyTo(output) }
                    } ?: error("No output stream")
                }.onSuccess {
                    Toast.makeText(this, "Datei gespeichert.", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, "Datei konnte nicht gespeichert werden.", Toast.LENGTH_LONG).show()
                }
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_SOURCE_PATH = "source_path"
        const val EXTRA_MIME_TYPE = "mime_type"
        const val EXTRA_FILE_NAME = "file_name"
        private const val REQUEST_CREATE_DOCUMENT = 4107
    }
}
