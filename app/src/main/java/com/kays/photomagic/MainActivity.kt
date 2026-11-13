package com.kays.photomagic

import android.app.Activity
import android.util.Base64
import org.json.JSONObject
import java.net.URL
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
    private var adultConfirmed = false
    private val pink = Color.rgb(245, 49, 159)
    private val purple = Color.rgb(113, 38, 175)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 28, 22, 18); setBackgroundColor(Color.rgb(250, 237, 255)) }
        val title = TextView(this).apply { text = "✨ Kay's Photo Magic ✨"; textSize = 25f; setTextColor(purple); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }
        root.addView(title)
        root.addView(TextView(this).apply { text = "Create • Edit • Enhance"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(purple) })
        prompt = EditText(this).apply { hint = "Describe the image you want to create or how to edit your photo"; minLines = 2; maxLines = 4; setTextColor(Color.BLACK); setHintTextColor(Color.DKGRAY) }
        root.addView(prompt)
        root.addView(TextView(this).apply { text = "Choose AI model"; setTextColor(purple); textSize = 17f })
        modelPicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("kayla.AI.7717 (local engine — setup needed)", "OpenAI GPT Image (connection needed)", "OpenAI GPT Image Mini (connection needed)", "Custom AI provider (coming soon)"))
        }
        root.addView(modelPicker)
        root.addView(TextView(this).apply { text = "Kayla engine mode"; setTextColor(purple); textSize = 17f })
        kaylaMode = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Auto: online premier, offline fallback", "Online premier only", "Offline only"))
        }
        root.addView(kaylaMode)
        apiKey = EditText(this).apply {
            hint = "OpenAI API key (not stored)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.BLACK)
            setHintTextColor(Color.DKGRAY)
        }
        root.addView(apiKey)
        root.addView(button("⚙ Check Kayla engine status") {
            val online = android.net.ConnectivityManager::class.java.let {
                val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                cm.activeNetworkInfo?.isConnected == true
            }
            val localReady = false // Replace only after a tested native inference engine and model are installed.
            status.text = "Internet: " + (if (online) "connected" else "unavailable") +
                ". Online image provider: not configured. Offline image model: not installed." +
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
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(button("✨ Enhance photo (offline)") { enhance() })
        root.addView(button("🎨 Generate AI image") { generateImage() })
        root.addView(button("💾 Save photo") { savePhoto() })
        root.addView(button("↗ Share photo") { sharePhoto() })
        status = TextView(this).apply { text = "Tap 📎 to select a photo. Offline enhancement is ready."; textSize = 14f; setTextColor(purple); setPadding(0, 12, 0, 0) }
        root.addView(status)
        setContentView(root)
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
                contentResolver.openInputStream(uri)?.use { current = BitmapFactory.decodeStream(it) }
                preview.setImageBitmap(current)
                val count = data.clipData?.itemCount ?: 1
                status.text = "$count photo(s) selected. First photo shown; multi-photo combining will require an image provider."
            } catch (e: Exception) { status.text = "Could not open photo: ${e.message}" }
        }
    }
    private fun generateImage() {
        if (!adultConfirmed) { status.text = "Please confirm you are 18 or older first."; return }
        val description = prompt.text.toString().trim()
        if (description.isEmpty()) { status.text = "Enter an image description first."; return }
        val selected = modelPicker.selectedItemPosition
        if (selected == 3) { status.text = "Custom provider is not connected yet."; return }
        if (selected == 0 && kaylaMode.selectedItemPosition == 2) {
            status.text = "Kayla offline image model is not installed on this device yet."
            return
        }
        val key = apiKey.text.toString().trim()
        if (key.isEmpty()) { status.text = "Enter your OpenAI API key for online generation. It will not be saved."; return }
        val model = if (selected == 2) "gpt-image-1-mini" else "gpt-image-1"
        status.text = "Requesting image from $model..."
        Thread {
            try {
                val connection = URL("https://api.openai.com/v1/images/generations").openConnection() as HttpsURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 120000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $key")
                connection.setRequestProperty("Content-Type", "application/json")
                val payload = JSONObject().put("model", model).put("prompt", description).put("size", "1024x1024")
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                connection.disconnect()
                if (code !in 200..299) {
                    val message = try { JSONObject(response).optJSONObject("error")?.optString("message") ?: "HTTP $code" } catch (_: Exception) { "HTTP $code" }
                    throw IllegalStateException(message)
                }
                val encoded = JSONObject(response).getJSONArray("data").getJSONObject(0).getString("b64_json")
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
        val w = (src.width * 1.5).toInt().coerceAtMost(4096)
        val h = (src.height * 1.5).toInt().coerceAtMost(4096)
        current = Bitmap.createScaledBitmap(src, w, h, true)
        preview.setImageBitmap(current)
        status.text = "Photo resized with smoothing. This is basic offline enhancement, not AI upscaling."
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
