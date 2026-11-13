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
        root.addView(button("✨ Enhance photo (offline)") { enhance() })
        root.addView(button("🎨 Generate from text") { generateImage(false) })
        root.addView(button("🪄 Modify uploaded photo") { generateImage(true) })
        root.addView(button("💾 Save photo") { savePhoto() })
        root.addView(button("↗ Share photo") { sharePhoto() })
        status = TextView(this).apply { text = "Tap 📎 to select a photo. Offline enhancement is ready."; textSize = 14f; setTextColor(purple); setPadding(0, 12, 0, 0) }
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
        if (selected == 0 && edit) { status.text = "Cloudflare free mode currently supports text-to-image only. For uploaded-photo editing, select an OpenAI model."; return }
        if (selected == 0 && accountId.text.toString().trim().isEmpty()) { status.text = "Enter your Cloudflare Account ID."; return }
        val model = if (selected == 2) "gpt-image-1-mini" else "gpt-image-1"
        val cfAccount = accountId.text.toString().trim()
        val selectedPhoto = if (edit) current else null
        status.text = if (edit) "Editing uploaded photo..." else "Generating online image..."
        Thread {
            try {
                val endpoint = if (selected == 0) "https://api.cloudflare.com/client/v4/accounts/$cfAccount/ai/run/@cf/black-forest-labs/flux-1-schnell" else if (edit) "https://api.openai.com/v1/images/edits" else "https://api.openai.com/v1/images/generations"
                val connection = URL(endpoint).openConnection() as HttpsURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 120000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $key")
                if (edit) {
                    val boundary = "KaylaBoundary" + System.currentTimeMillis()
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    val photo = ByteArrayOutputStream().also { selectedPhoto!!.compress(Bitmap.CompressFormat.PNG, 95, it) }.toByteArray()
                    connection.outputStream.use { out ->
                        fun part(name: String, value: String) {
                            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
                        }
                        part("model", model)
                        part("prompt", description)
                        part("size", "1024x1024")
                        out.write("--$boundary\r\nContent-Disposition: form-data; name=\"image\"; filename=\"photo.png\"\r\nContent-Type: image/png\r\n\r\n".toByteArray())
                        out.write(photo)
                        out.write("\r\n--$boundary--\r\n".toByteArray())
                    }
                } else {
                    connection.setRequestProperty("Content-Type", "application/json")
                    val payload = if (selected == 0) JSONObject().put("prompt", description).put("num_steps", 4) else JSONObject().put("model", model).put("prompt", description).put("size", "1024x1024")
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
                runOnUiThread { current = result; preview.setImageBitmap(result); status.text = "Image ready! Tap Save to add it to your gallery." }
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
            current = Bitmap.createScaledBitmap(src, w, h, true)
            preview.setImageBitmap(current)
            status.text = "Photo resized with smoothing. This is basic offline enhancement, not AI upscaling."
        } catch (e: OutOfMemoryError) { status.text = "Not enough phone memory to enhance this photo." }
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
