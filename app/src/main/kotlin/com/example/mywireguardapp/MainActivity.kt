package com.example.wireguardapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import java.net.InetAddress

class MainActivity : AppCompatActivity() {

    private lateinit var backend: GoBackend
    private var currentTunnel: Tunnel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        backend = GoBackend(this)

        val etConfig = findViewById<EditText>(R.id.etConfig)
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        val btnDisconnect = findViewById<Button>(R.id.btnDisconnect)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        btnConnect.setOnClickListener {
            connectVPN(etConfig.text.toString(), tvStatus)
        }

        btnDisconnect.setOnClickListener {
            disconnectVPN(tvStatus)
        }
    }

    private fun connectVPN(configText: String, statusView: TextView) {
        try {
            val config = Config.parse(configText)
            val tunnelName = "mywg"

            currentTunnel = backend.createTunnel(tunnelName, config)
            statusView.text = "เชื่อมต่อสำเร็จ!"
        } catch (e: Exception) {
            statusView.text = "ผิดพลาด: ${e.message}"
        }
    }

    private fun disconnectVPN(statusView: TextView) {
        currentTunnel?.let {
            it.setState(Tunnel.State.DOWN)
            statusView.text = "ตัดการเชื่อมต่อแล้ว"
        }
    }
}