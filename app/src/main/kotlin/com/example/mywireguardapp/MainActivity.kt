package com.example.mywireguardapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var backend: GoBackend
    private var currentTunnel: WgTunnel? = null
    private val tunnelName = "mywg"
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        backend = GoBackend(this)

        val etConfig = findViewById<EditText>(R.id.etConfig)
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        val btnDisconnect = findViewById<Button>(R.id.btnDisconnect)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        btnConnect.setOnClickListener {
            val configText = etConfig.text.toString().trim()
            if (configText.isEmpty()) {
                Toast.makeText(this, "กรุณาวาง Config ก่อน", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            connectVPN(configText, tvStatus)
        }

        btnDisconnect.setOnClickListener {
            disconnectVPN(tvStatus)
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
                        statusView.text = "กำลังขอสิทธิ์ VPN..."
                    }
                    return@launch
                }

                val inputStream = ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8))
                val config = Config.parse(inputStream)

                currentTunnel = WgTunnel(tunnelName)
                
                withContext(Dispatchers.Main) {
                    backend.setState(currentTunnel!!, Tunnel.State.UP, config)
                    statusView.text = "✅ เชื่อมต่อสำเร็จ!"
                    Toast.makeText(this@MainActivity, "WireGuard เชื่อมต่อแล้ว", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                val errorMsg = e.message ?: e.toString()
                withContext(Dispatchers.Main) {
                    statusView.text = "❌ Error: $errorMsg"
                    Toast.makeText(this@MainActivity, "เชื่อมต่อล้มเหลว", Toast.LENGTH_LONG).show()
                }
                e.printStackTrace()
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
                    statusView.text = "❌ Disconnect Error: ${e.message}"
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