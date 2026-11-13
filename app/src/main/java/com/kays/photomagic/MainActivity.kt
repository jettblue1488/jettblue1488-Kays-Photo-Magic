package com.kays.photomagic

import android.app.Activity
import android.util.Base64
import org.json.JSONObject
import java.net.URL
import java.io.ByteArrayOutputStream
import javax.net.ssl.HttpsURLConnection
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {
    private lateinit var preview: ImageView
    private lateinit var prompt: EditText
    private lateinit var status: TextView
    private var current: Bitmap? = null
    private var previous: Bitmap? = null
    private lateinit var editChoice: Spinner
    private lateinit var modelPicker: Spinner
    private lateinit var kaylaMode: Spinner
    private lateinit var apiKey: EditText
    private lateinit var accountId: EditText
    private var adultConfirmed = false
    private val pink = Color.rgb(245, 49, 159)
    private val purple = Color.rgb(113, 38, 175)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 28, 22, 18); setBackgroundColor(Color.rgb(250, 237, 255)) }
        val title = TextView(this).apply { text = "✨ Kay's Photo Magic ✨"; textSize = 25f; setTextColor(purple); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }
        root.addView(title)
        root.addView(TextView(this).apply { text = "Create • Edit • Enhance"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(purple) })
        prompt = EditText(this).apply { hint = "Describe the image you want to create or how to edit your photo"; minLines = 2; maxLines = 4; setTextColor(Color.BLACK); setHintTextColor(Color.DKGRAY) }
        root.addView(prompt)
        root.addView(TextView(this).apply { text = "Choose AI model"; setTextColor(purple); textSize = 17f })
        modelPicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Kayla Free Online (Cloudflare)", "OpenAI GPT Image", "OpenAI GPT Image Mini"))
        }
        root.addView(modelPicker)
        root.addView(TextView(this).apply { text = "Online image generation • 18+ confirmation required • Provider content rules apply"; setTextColor(purple); textSize = 14f })
        kaylaMode = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Online Premier (active)"))
        }

        apiKey = EditText(this).apply {
            hint = "Provider API token (not stored)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.BLACK)
            setHintTextColor(Color.DKGRAY)
        }
        root.addView(apiKey)
        accountId = EditText(this).apply { hint = "Cloudflare Account ID (free mode only)"; setText("bc3dc03fda83ed5f4d308fa210015b97"); setTextColor(Color.BLACK); setHintTextColor(Color.DKGRAY) }
        root.addView(accountId)
        root.addView(button("⚙ Check Kayla engine status") {
            val online = android.net.ConnectivityManager::class.java.let {
                val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                cm.activeNetworkInfo?.isConnected == true
            }
            val localReady = false // Replace only after a tested native inference engine and model are installed.
            status.text = "Internet: " + (if (online) "connected" else "unavailable") +
                ". Online Premier: enter an API key to generate. Offline image model: not installed." +
                (if (localReady) " Local engine ready." else " Generation requires engine setup.")
        })
        root.addView(button("🔞 Confirm I am 18+") {
            AlertDialog.Builder(this).setTitle("18+ confirmation")
                .setMessage("Are you 18 years of age or older? AI providers still enforce their content policies.")
                .setPositiveButton("Yes, 18+") { _, _ -> adultConfirmed = true; status.text = "18+ confirmed for this session." }
                .setNegativeButton("No") { _, _ -> adultConfirmed = false; status.text = "Adult mode disabled." }
                .show()
        })
        val pick = button("📎 Add reference / edit photo") { pickImages() }
        root.addView(pick)
        preview = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER; minimumHeight = 190; setBackgroundColor(Color.WHITE) }
        root.addView(preview, LinearLayout.LayoutParams(-1, 240))
        root.addView(TextView(this).apply { text = "Offline editing choices"; setTextColor(purple); textSize = 17f })
        editChoice = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Brighten", "Darken", "Grayscale", "Warm colors", "Cool colors", "Mirror horizontally", "Rotate right", "Smooth enlarge"))
        }
        root.addView(editChoice)
        root.addView(button("🎛 Apply selected offline edit") { applyOfflineEdit() })
        root.addView(button("↶ Undo last edit") { previous?.let { current = it; preview.setImageBitmap(it); previous = null; status.text = "Last edit undone." } })
        root.addView(button("📚 My saved photos") { showLibrary() })
        root.addView(button("📝 Open text document") { openDocument() })
        root.addView(button("🔤 Add or replace text on photo") { editPhotoText() })
        root.addView(button("✨ Enhance photo (offline)") { enhance() })
        root.addView(button("🎨 Generate from text") { generateImage(false) })
        root.addView(button("🪄 Modify uploaded photo") { generateImage(true) })
        root.addView(button("💾 Save photo") { savePhoto() })
        root.addView(button("↗ Share photo") { sharePhoto() })
        status = TextView(this).apply { text = "Kayla Free Online supports both text generation and photo editing with your Cloudflare token."; textSize = 14f; setTextColor(purple); setPadding(0, 12, 0, 0) }
        root.addView(status)
        scroll.addView(root)
        setContentView(scroll)
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; setTextColor(Color.WHITE); setBackgroundTintList(android.content.res.ColorStateList.valueOf(if(label.contains("📎")) purple else pink)); setOnClickListener { action() }
    }
    private fun pickImages() {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) }
        startActivityForResult(Intent.createChooser(i, "Choose reference photos"), 100)
    }
    @Deprecated("Legacy activity result used for Android 6 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK && data?.data != null) {
            try {
                val content = contentResolver.openInputStream(data.data!!)?.bufferedReader()?.use { it.readText() } ?: ""
                showDocumentEditor(content)
            } catch (e: Exception) { status.text = "Could not read document: ${e.message}" }
            return
        }
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            val uri = data.data ?: data.clipData?.getItemAt(0)?.uri ?: return
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    val longest = maxOf(options.outWidth, options.outHeight)
                    val sample = generateSequence(1) { it * 2 }.first { longest / it <= 1600 }
                    contentResolver.openInputStream(uri)?.use { imageStream ->
                        current = BitmapFactory.decodeStream(imageStream, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                }
                preview.setImageBitmap(current)
                saveToLibrary("Imported")
                val count = data.clipData?.itemCount ?: 1
                status.text = "$count photo(s) selected. First photo shown; multi-photo combining will require an image provider."
            } catch (e: Exception) { status.text = "Could not open photo: ${e.message}" }
        }
    }
    private fun generateImage(edit: Boolean) {
        if (!adultConfirmed) { status.text = "Please confirm you are 18 or older first."; return }
        val description = prompt.text.toString().trim()
        if (description.isEmpty()) { status.text = "Enter an image description first."; return }
        val selected = modelPicker.selectedItemPosition
        if (edit && current == null) { status.text = "Select a photo to edit first."; return }
        val key = apiKey.text.toString().trim()
        if (key.isEmpty()) { status.text = "Enter your provider API token. It will not be saved."; return }
        
        if (selected == 0 && accountId.text.toString().trim().isEmpty()) { status.text = "Enter your Cloudflare Account ID."; return }
        val model = if (selected == 2) "gpt-image-1-mini" else "gpt-image-1"
        val cfAccount = accountId.text.toString().trim()
        val selectedPhoto = if (edit) current else null
        status.text = if (edit) "Editing uploaded photo..." else "Generating online image..."
        Thread {
            try {
                val endpoint = if (selected == 0) "https://api.cloudflare.com/client/v4/accounts/$cfAccount/ai/run/@cf/black-forest-labs/flux-2-klein-4b" else if (edit) "https://api.openai.com/v1/images/edits" else "https://api.openai.com/v1/images/generations"
                val connection = URL(endpoint).openConnection() as HttpsURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 120000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $key")
                if (edit || selected == 0) {
                    val boundary = "KaylaBoundary" + System.currentTimeMillis()
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    val photo = if (edit) {
                        val original = selectedPhoto!!
                        val ratio = minOf(500f / original.width, 500f / original.height, 1f)
                        val scaled = Bitmap.createScaledBitmap(original, (original.width * ratio).toInt().coerceAtLeast(1), (original.height * ratio).toInt().coerceAtLeast(1), true)
                        ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.PNG, 90, it) }.toByteArray()
                    } else null
                    connection.outputStream.use { out ->
                        fun part(name: String, value: String) {
                            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
                        }
                        if (selected != 0) { part("model", model); part("size", "1024x1024") }
                        part("prompt", description)
                        if (selected == 0) { part("width", "768"); part("height", "768") }
                        if (photo != null) {
                            val field = if (selected == 0) "input_image_0" else "image"
                            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$field\"; filename=\"photo.png\"\r\nContent-Type: image/png\r\n\r\n".toByteArray())
                            out.write(photo)
                            out.write("\r\n".toByteArray())
                        }
                        out.write("--$boundary--\r\n".toByteArray())
                    }
                } else {
                    connection.setRequestProperty("Content-Type", "application/json")
                    val payload = JSONObject().put("model", model).put("prompt", description).put("size", "1024x1024")
                    connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                connection.disconnect()
                if (code !in 200..299) {
                    val message = try { JSONObject(response).optJSONObject("error")?.optString("message") ?: response.take(250) } catch (_: Exception) { "HTTP $code" }
                    throw IllegalStateException(message)
                }
                val json = JSONObject(response)
                val encoded = if (selected == 0) json.getJSONObject("result").getString("image") else json.getJSONArray("data").getJSONObject(0).getString("b64_json")
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                val result = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Image decode failed")
                runOnUiThread { current = result; preview.setImageBitmap(result); saveToLibrary("Generated"); status.text = "Image generated and saved to My saved photos." }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Online generation failed: ${e.message}. Kayla offline generation is not installed; no automatic image fallback is available yet."
                }
            }
        }.start()
    }

    private fun enhance() {
        val src = current ?: run { status.text = "Select a photo first."; return }
        try {
            val w = (src.width * 1.5).toInt().coerceAtMost(2048)
            val h = (src.height * 1.5).toInt().coerceAtMost(2048)
            previous = src
            current = Bitmap.createScaledBitmap(src, w, h, true)
            preview.setImageBitmap(current)
            saveToLibrary("Edited")
            status.text = "Photo resized with smoothing. This is basic offline enhancement, not AI upscaling."
        } catch (e: OutOfMemoryError) { status.text = "Not enough phone memory to enhance this photo." }
    }


    private fun openDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
        }
        startActivityForResult(intent, 101)
    }
    private fun showDocumentEditor(content: String) {
        val editor = EditText(this).apply {
            setText(content)
            minLines = 10
            gravity = Gravity.TOP
            setTextIsSelectable(true)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 12)
            addView(TextView(this@MainActivity).apply { text = "Tap a word to select it, then type your correction." })
            addView(ScrollView(this@MainActivity).apply { addView(editor) }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        AlertDialog.Builder(this).setTitle("Text document editor")
            .setView(layout)
            .setPositiveButton("Save copy") { _, _ ->
                try {
                    val dir = File(filesDir, "documents").apply { mkdirs() }
                    val file = File(dir, "Edited-${System.currentTimeMillis()}.txt")
                    file.writeText(editor.text.toString())
                    status.text = "Document saved privately: ${file.name}"
                    showSavedDocument(file)
                } catch (e: Exception) { status.text = "Document save failed: ${e.message}" }
            }.setNegativeButton("Cancel", null).show()
    }
    private fun showSavedDocument(file: File) {
        AlertDialog.Builder(this).setTitle("Document saved")
            .setMessage("Your edited text is saved in the app. Export a copy to keep it if you uninstall.")
            .setPositiveButton("Share text") { _, _ ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, file.readText())
                }, "Export edited document"))
            }.setNegativeButton("Close", null).show()
    }
    private fun editPhotoText() {
        val src = current ?: run { status.text = "Upload or select a photo first."; return }
        val entry = EditText(this).apply { hint = "Type replacement or new text"; setSingleLine(false) }
        AlertDialog.Builder(this).setTitle("Add text to photo")
            .setMessage("This first version places text on the photo. It does not automatically erase existing words.")
            .setView(entry)
            .setPositiveButton("Apply") { _, _ ->
                val words = entry.text.toString()
                if (words.isNotBlank()) {
                    val out = src.copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = android.graphics.Canvas(out)
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = (out.width / 18f).coerceIn(18f, 80f)
                        typeface = Typeface.DEFAULT_BOLD
                        setShadowLayer(2f, 1f, 1f, Color.WHITE)
                    }
                    canvas.drawText(words, out.width * 0.08f, out.height * 0.5f, paint)
                    previous = src
                    current = out
                    preview.setImageBitmap(out)
                    saveToLibrary("TextEdited")
                    status.text = "Text added and saved. Use Undo to revert."
                }
            }.setNegativeButton("Cancel", null).show()
    }
    private fun libraryDir(): File = File(filesDir, "photo_library").apply { mkdirs() }
    private fun saveToLibrary(category: String) {
        val image = current ?: return
        try {
            val file = File(libraryDir(), "${category}_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { image.compress(Bitmap.CompressFormat.PNG, 95, it) }
        } catch (e: Exception) { status.text = "Library save failed: ${e.message}" }
    }
    private fun showLibrary() {
        val files = libraryDir().listFiles()?.filter { it.extension == "png" }?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (files.isEmpty()) { status.text = "Your photo library is empty."; return }
        val names = files.map { "${it.nameWithoutExtension.substringBefore('_')} • " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.lastModified())) }.toTypedArray()
        AlertDialog.Builder(this).setTitle("My saved photos (${files.size})").setItems(names) { _, which ->
            val bitmap = BitmapFactory.decodeFile(files[which].absolutePath)
            if (bitmap != null) { current = bitmap; preview.setImageBitmap(bitmap); status.text = "Loaded saved photo." }
        }.setNegativeButton("Close", null).show()
    }
    private fun applyOfflineEdit() {
        val src = current ?: run { status.text = "Choose a photo first."; return }
        try {
            previous = src
            val choice = editChoice.selectedItemPosition
            val result = when (choice) {
                6 -> { val m = android.graphics.Matrix().apply { postRotate(90f) }; Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true) }
                5 -> { val m = android.graphics.Matrix().apply { preScale(-1f, 1f) }; Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true) }
                7 -> Bitmap.createScaledBitmap(src, (src.width * 1.5f).toInt().coerceAtMost(2048), (src.height * 1.5f).toInt().coerceAtMost(2048), true)
                else -> {
                    val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(output)
                    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                    val cm = when (choice) {
                        0 -> android.graphics.ColorMatrix(floatArrayOf(1f,0f,0f,0f,25f,0f,1f,0f,0f,25f,0f,0f,1f,0f,25f,0f,0f,0f,1f,0f))
                        1 -> android.graphics.ColorMatrix(floatArrayOf(1f,0f,0f,0f,-25f,0f,1f,0f,0f,-25f,0f,0f,1f,0f,-25f,0f,0f,0f,1f,0f))
                        2 -> android.graphics.ColorMatrix().apply { setSaturation(0f) }
                        3 -> android.graphics.ColorMatrix(floatArrayOf(1.12f,0f,0f,0f,8f,0f,1f,0f,0f,0f,0f,0f,0.85f,0f,0f,0f,0f,0f,1f,0f))
                        else -> android.graphics.ColorMatrix(floatArrayOf(0.88f,0f,0f,0f,0f,0f,1f,0f,0f,0f,0f,0f,1.15f,0f,8f,0f,0f,0f,1f,0f))
                    }
                    paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
                    canvas.drawBitmap(src, 0f, 0f, paint)
                    output
                }
            }
            current = result
            preview.setImageBitmap(result)
            saveToLibrary("Edited")
            status.text = "Offline edit applied and saved. Undo is available."
        } catch (e: Exception) { status.text = "Edit failed: ${e.message}" }
    }
    private fun photoFile(): File {
        val file = File(getExternalFilesDir(null), "KaysPhotoMagic-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { current!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }
    private fun savePhoto() {
        if (current == null) { status.text = "Select a photo first."; return }
        try {
            val name = "KaysPhotoMagic-${System.currentTimeMillis()}"
            val uri = MediaStore.Images.Media.insertImage(contentResolver, current, name, "Made with Kay's Photo Magic")
            status.text = if (uri != null) "Saved to gallery!" else "Save failed."
        } catch (e: Exception) { status.text = "Save failed: ${e.message}" }
    }
    private fun sharePhoto() {
        if (current == null) { status.text = "Select a photo first."; return }
        try {
            val uri = MediaStore.Images.Media.insertImage(contentResolver, current, "KaysPhotoMagic", "Shared photo")
            if (uri == null) { status.text = "Could not prepare share."; return }
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(uri)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share photo"))
        } catch (e: Exception) { status.text = "Share failed: ${e.message}" }
    }
}
