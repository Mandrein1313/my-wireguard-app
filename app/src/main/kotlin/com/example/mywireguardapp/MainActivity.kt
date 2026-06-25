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
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var backend: GoBackend
    private var currentTunnel: Tunnel? = null
    private var tunnelName = "mywg"

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
        try {
            // เตรียม VPN Permission
            val intent = GoBackend.VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, 1)
                statusView.text = "กำลังขอสิทธิ์ VPN..."
                return
            }

            val config = Config.parse(ByteArrayInputStream(configText.toByteArray()))
            
            currentTunnel = backend.createTunnel(tunnelName)  // อาจใช้ createTunnel หรือ setState โดยตรง

            backend.setState(currentTunnel!!, Tunnel.State.UP, config)

            statusView.text = "✅ เชื่อมต่อสำเร็จแล้ว"
            Toast.makeText(this, "เชื่อมต่อ WireGuard สำเร็จ", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            statusView.text = "❌ ผิดพลาด: ${e.message}"
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun disconnectVPN(statusView: TextView) {
        try {
            currentTunnel?.let {
                backend.setState(it, Tunnel.State.DOWN, null)
                statusView.text = "⛔ ตัดการเชื่อมต่อแล้ว"
                Toast.makeText(this, "ตัดการเชื่อมต่อแล้ว", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            statusView.text = "❌ Error: ${e.message}"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            // ผู้ใช้ให้สิทธิ์แล้ว ลองเชื่อมต่อใหม่
            val etConfig = findViewById<EditText>(R.id.etConfig)
            connectVPN(etConfig.text.toString().trim(), findViewById(R.id.tvStatus))
        }
    }
}