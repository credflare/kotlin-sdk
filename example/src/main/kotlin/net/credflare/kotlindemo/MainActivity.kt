package net.credflare.kotlindemo

import android.app.Activity
import android.os.Bundle
import android.widget.*
import net.credflare.sdk.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity: Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(48,80,48,48) }
        page.addView(TextView(this).apply { text="Native Android SDK demo"; textSize=24f })
        page.addView(TextView(this).apply { text="Choose a sample profile. The SDK follows the capture rules returned by the backend."; textSize=16f; setPadding(0,24,0,24) })
        val modes=listOf("either","snapshot","upload","snapshot_and_upload")
        val picker=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Photo or file","Camera photos","File uploads","Photo and saved file")) }
        page.addView(picker)
        val status=TextView(this)
        page.addView(Button(this).apply {
            text="Start verification"; isAllCaps=false
            setOnClickListener {
                val mode=modes[picker.selectedItemPosition]
                CredflareVerification.launch(this@MainActivity,SessionProvider {
                    val connection=URL("http://127.0.0.1:3107/session").openConnection() as HttpURLConnection
                    connection.connectTimeout=30000; connection.readTimeout=30000; connection.instanceFollowRedirects=false
                    connection.requestMethod="POST"; connection.doOutput=true
                    connection.setRequestProperty("Content-Type","application/json")
                    try {
                        val body=JSONObject().put("email","native-demo@example.test").put("mode",mode).toString()
                        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                        require(connection.responseCode == 200) { "The local demo backend could not create a session." }
                        SessionDescriptor.fromJson(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }))
                    } finally { connection.disconnect() }
                }) { result -> status.text=when(result) { VerificationResult.SUBMITTED -> "Documents received for review."; VerificationResult.CANCELLED -> "Verification closed."; VerificationResult.HOST_ACTION_REQUIRED -> "Return to your app for the remaining checks."; VerificationResult.FAILED -> "Restart verification from this app." } }
            }
        })
        page.addView(status)
        page.addView(TextView(this).apply { text="Sandbox demo. Use sample documents."; textSize=12f; setPadding(0,32,0,0) })
        setContentView(page)
    }
}
