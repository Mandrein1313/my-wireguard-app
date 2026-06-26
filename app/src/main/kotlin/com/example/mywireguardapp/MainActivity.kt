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
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var backend: GoBackend
    private var currentTunnel: WgTunnel? = null
    private val tunnelName = "mywg"
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val configList = mutableListOf<WireGuardConfig>()
    private lateinit var configAdapter: ArrayAdapter<String>

    private lateinit var etConfig: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvTraffic: TextView
    private lateinit var spinnerConfigs: Spinner

    data class WireGuardConfig(val name: String, val content: String)

    private var trafficJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        backend = GoBackend(this)

        etConfig = findViewById(R.id.etConfig)
        tvStatus = findViewById(R.id.tvStatus)
        tvTraffic = findViewById(R.id.tvTraffic)
        spinnerConfigs = findViewById(R.id.spinnerConfigs)

        // Setup Spinner
        configAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf())
        configAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerConfigs.adapter = configAdapter

        spinnerConfigs.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long) {
                val cfg = configList[pos]
                etConfig.setText(cfg.content)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        findViewById<Button>(R.id.btnScanQR).setOnClickListener { checkCameraPermissionAndScan() }
        findViewById<Button>(R.id.btnImport).setOnClickListener { importFile() }
        findViewById<Button>(R.id.btnConnect).setOnClickListener { connectVPN() }
        findViewById<Button>(R.id.btnDisconnect).setOnClickListener { disconnectVPN() }
    }

    // ==================== QR CODE SCANNER ====================
    private fun checkCameraPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        } else {
            startQRScan()
        }
    }

    private fun startQRScan() {
        Toast.makeText(this, "เปิดกล้องสแกน QR Code...", Toast.LENGTH_SHORT).show()
        
        // ใช้ ML Kit ง่าย ๆ (เวอร์ชันเต็มต้องใช้ CameraX Preview)
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        
        val scanner = BarcodeScanning.getClient(options)
        
        // หมายเหตุ: สำหรับเวอร์ชันเต็ม ควรสร้าง Camera Preview Activity
        // แต่เพื่อความง่าย ใช้ Intent เลือกรูปภาพ QR Code
        val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            // ใช้ ML Kit กับรูปภาพ (เวอร์ชันง่าย)
            Toast.makeText(this, "QR Code Scanner (เวอร์ชันเต็ม) อยู่ระหว่างพัฒนา\nใช้ Import .conf ชั่วคราว", Toast.LENGTH_LONG).show()
        }
        pickImage.launch("image/*")
    }

    // ==================== IMPORT FILE ====================
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readConfigFile(it) }
    }

    private fun importFile() {
        importLauncher.launch(arrayOf("text/*", "*/*"))
    }

    private fun readConfigFile(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            if (content.contains("[Interface]")) {
                val name = "Config-${System.currentTimeMillis()}"
                configList.add(WireGuardConfig(name, content))
                refreshSpinner()
                etConfig.setText(content)
                Toast.makeText(this, "✅ Import สำเร็จ", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Import ล้มเหลว", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshSpinner() {
        val names = configList.map { it.name }
        configAdapter.clear()
        configAdapter.addAll(names)
        configAdapter.notifyDataSetChanged()
    }

    // ==================== CONNECT & TRAFFIC ====================
    private fun connectVPN() {
        val configText = etConfig.text.toString().trim()
        if (configText.isEmpty()) return

        tvStatus.text = "กำลังเชื่อมต่อ..."

        scope.launch(Dispatchers.IO) {
            try {
                val prepare = GoBackend.VpnService.prepare(this@MainActivity)
                if (prepare != null) {
                    withContext(Dispatchers.Main) { startActivityForResult(prepare, 100) }
                    return@launch
                }

                val config = Config.parse(ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8)))
                currentTunnel = WgTunnel(tunnelName)
                backend.setState(currentTunnel!!, Tunnel.State.UP, config)

                withContext(Dispatchers.Main) {
                    tvStatus.text = "✅ เชื่อมต่อสำเร็จ"
                    startTrafficMonitor()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "❌ ${e.message}"
                }
            }
        }
    }

    private fun startTrafficMonitor() {
        trafficJob?.cancel()
        trafficJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000)
                try {
                    val stats = backend.getTunnelStatistics(currentTunnel?.getName() ?: tunnelName)
                    withContext(Dispatchers.Main) {
                        tvTraffic.text = "Traffic: ↓ ${stats.rxBytes / 1024} KB | ↑ ${stats.txBytes / 1024} KB"
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun disconnectVPN() {
        trafficJob?.cancel()
        scope.launch(Dispatchers.IO) {
            currentTunnel?.let {
                backend.setState(it, Tunnel.State.DOWN, null)
                withContext(Dispatchers.Main) {
                    tvStatus.text = "⛔ ตัดการเชื่อมต่อแล้ว"
                    tvTraffic.text = "Traffic: rx: 0 B | tx: 0 B"
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
        trafficJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}