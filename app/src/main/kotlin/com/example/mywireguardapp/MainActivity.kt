package com.example.mywireguardapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var backend: GoBackend
    private var currentTunnel: WgTunnel? = null
    private val tunnelName = "mywg"
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val importFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { readConfigFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        backend = GoBackend(this)

        val etConfig = findViewById<EditText>(R.id.etConfig)
        val btnImport = findViewById<Button>(R.id.btnImport)
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        val btnDisconnect = findViewById<Button>(R.id.btnDisconnect)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        btnImport.setOnClickListener {
            importFileLauncher.launch("text/*")
        }

        btnConnect.setOnClickListener {
            val configText = etConfig.text.toString().trim()
            if (configText.isEmpty()) {
                Toast.makeText(this, "กรุณาวาง Config หรือ Import ไฟล์", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            connectVPN(configText, tvStatus)
        }

        btnDisconnect.setOnClickListener { disconnectVPN(tvStatus) }
    }

    private fun readConfigFromUri(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val configText = inputStream?.bufferedReader().use { it?.readText() } ?: ""
            findViewById<EditText>(R.id.etConfig).setText(configText)
            Toast.makeText(this, "Import Config สำเร็จ", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Import ล้มเหลว: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun connectVPN(configText: String, statusView: TextView) {
        statusView.text = "กำลังเชื่อมต่อ..."
        
        scope.launch(Dispatchers.IO) {
            try {
                val prepareIntent = GoBackend.VpnService.prepare(this@MainActivity)
                if (prepareIntent != null) {
                    withContext(Dispatchers.Main) {
                        startActivityForResult(prepareIntent, 100)
                    }
                    return@launch
                }

                val inputStream = ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8))
                val config = Config.parse(inputStream)

                currentTunnel = WgTunnel(tunnelName)
                backend.setState(currentTunnel!!, Tunnel.State.UP, config)

                withContext(Dispatchers.Main) {
                    statusView.text = "✅ เชื่อมต่อสำเร็จ (MTU อัตโนมัติ)"
                    Toast.makeText(this@MainActivity, "เชื่อมต่อสำเร็จ", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                val errorMsg = e.message ?: e.toString()
                withContext(Dispatchers.Main) {
                    statusView.text = "❌ Error: $errorMsg"
                    Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun disconnectVPN(statusView: TextView) {
        scope.launch(Dispatchers.IO) {
            try {
                currentTunnel?.let {
                    backend.setState(it, Tunnel.State.DOWN, null)
                    withContext(Dispatchers.Main) {
                        statusView.text = "⛔ ตัดการเชื่อมต่อแล้ว"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusView.text = "❌ Error: ${e.message}"
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val etConfig = findViewById<EditText>(R.id.etConfig)
            connectVPN(etConfig.text.toString().trim(), findViewById(R.id.tvStatus))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}