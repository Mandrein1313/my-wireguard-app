package com.example.mywireguardapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel   // ← สำคัญ
import com.wireguard.config.Config
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var backend: GoBackend
    private var currentTunnel: WgTunnel? = null
    private val tunnelName = "mywg"

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
        val prepareIntent = GoBackend.VpnService.prepare(this)
        if (prepareIntent != null) {
            startActivityForResult(prepareIntent, 100)
            statusView.text = "กำลังขอสิทธิ์ VPN..."
            return
        }

        val inputStream = ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8))
        val config = Config.parse(inputStream)

        currentTunnel = WgTunnel(tunnelName)
        
        backend.setState(currentTunnel!!, Tunnel.State.UP, config)

        statusView.text = "✅ เชื่อมต่อสำเร็จ!"
        Toast.makeText(this, "WireGuard เชื่อมต่อแล้ว", Toast.LENGTH_LONG).show()

    } catch (e: Exception) {
        val errorMsg = e.message ?: e.toString()
        statusView.text = "❌ Error: $errorMsg"
        Toast.makeText(this, "เชื่อมต่อล้มเหลว: $errorMsg", Toast.LENGTH_LONG).show()
        e.printStackTrace()
    }
}
 private fun disconnectVPN(statusView: TextView) {
    try {
        currentTunnel?.let {
            backend.setState(it, Tunnel.State.DOWN, null)
            statusView.text = "⛔ ตัดการเชื่อมต่อแล้ว"
        } ?: run {
            statusView.text = "⛔ ไม่มี Tunnel ที่เชื่อมอยู่"
        }
    } catch (e: Exception) {
        statusView.text = "❌ Disconnect Error: ${e.message}"
    }
}
}
