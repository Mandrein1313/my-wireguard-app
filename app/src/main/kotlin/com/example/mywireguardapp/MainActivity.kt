package com.example.mywireguardapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
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

    // เก็บ Config หลายตัว
    private val configList = mutableListOf<WireGuardConfig>()
    private lateinit var configAdapter: ArrayAdapter<String>

    private lateinit var etConfig: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvTraffic: TextView
    private lateinit var spinnerConfigs: Spinner

    data class WireGuardConfig(val name: String, val content: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        backend = GoBackend(this)

        etConfig = findViewById(R.id.etConfig)
        tvStatus = findViewById(R.id.tvStatus)
        tvTraffic = findViewById(R.id.tvTraffic)
        spinnerConfigs = findViewById(R.id.spinnerConfigs)

        // Setup Spinner สำหรับเลือก Config
        configAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf())
        configAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerConfigs.adapter = configAdapter

        spinnerConfigs.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long) {
                if (pos < configList.size) {
                    etConfig.setText(configList[pos].content)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // ปุ่มต่าง ๆ
        findViewById<Button>(R.id.btnScanQR).setOnClickListener {
            Toast.makeText(this, "📷 Scan QR Code ยังอยู่ระหว่างพัฒนา\nใช้ Import .conf ชั่วคราว", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btnImport).setOnClickListener { importConfigFile() }
        findViewById<Button>(R.id.btnConnect).setOnClickListener { connectVPN() }
        findViewById<Button>(R.id.btnDisconnect).setOnClickListener { disconnectVPN() }
    }

    // ==================== IMPORT .conf ====================
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { readConfigFile(it) }
    }

    private fun importConfigFile() {
        importLauncher.launch(arrayOf("text/*", "*/*"))
    }

    private fun readConfigFile(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            if (content.contains("[Interface]") && content.contains("PrivateKey")) {
                val name = "Config-${System.currentTimeMillis()}"
                configList.add(WireGuardConfig(name, content))
                refreshSpinner()
                etConfig.setText(content)
                Toast.makeText(this, "✅ Import .conf สำเร็จ", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "❌ ไม่ใช่ไฟล์ WireGuard Config", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Import ล้มเหลว: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshSpinner() {
        val names = configList.map { it.name }
        configAdapter.clear()
        configAdapter.addAll(names)
        configAdapter.notifyDataSetChanged()
    }

    // ==================== CONNECT / DISCONNECT ====================
private fun connectVPN() {
    val configText = etConfig.text.toString().trim()
    if (configText.isEmpty()) {
        Toast.makeText(this, "กรุณาใส่ Config", Toast.LENGTH_SHORT).show()
        return
    }

    tvStatus.text = "กำลังเชื่อมต่อ..."

    scope.launch(Dispatchers.IO) {
        try {
            val prepareIntent = GoBackend.VpnService.prepare(this@MainActivity)
            if (prepareIntent != null) {
                withContext(Dispatchers.Main) {
                    startActivityForResult(prepareIntent, 100)
                }
                return@launch
            }

            val config = Config.parse(ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8)))
            currentTunnel = WgTunnel(tunnelName)
            
            backend.setState(currentTunnel!!, Tunnel.State.UP, config)

            withContext(Dispatchers.Main) {
                tvStatus.text = "✅ เชื่อมต่อสำเร็จ"
                Toast.makeText(this@MainActivity, "WireGuard เชื่อมต่อแล้ว", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            val fullError = e.toString() + "\nCause: " + (e.cause?.message ?: "null")
            
            withContext(Dispatchers.Main) {
                tvStatus.text = "❌ Error: $errorMsg"
                Toast.makeText(this@MainActivity, fullError.take(400), Toast.LENGTH_LONG).show()
            }
            e.printStackTrace()
        }
    }
}

    private fun disconnectVPN() {
        scope.launch(Dispatchers.IO) {
            try {
                currentTunnel?.let {
                    backend.setState(it, Tunnel.State.DOWN, null)
                    withContext(Dispatchers.Main) {
                        tvStatus.text = "⛔ ตัดการเชื่อมต่อแล้ว"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "❌ Disconnect Error"
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            connectVPN()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
