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

class MainActivity : AppCompatActivity() {

    private lateinit var backend: GoBackend
    private var currentTunnel: WgTunnel? = null
    private val tunnelName = "mywg"
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // เก็บ Config หลายตัว
    private val configList = mutableListOf<WireGuardConfig>()
    private lateinit var configAdapter: ArrayAdapter<String>
    private var currentConfigName: String = ""

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

        // Setup Spinner
        configAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf())
        configAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerConfigs.adapter = configAdapter

        spinnerConfigs.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val selected = configList[position]
                currentConfigName = selected.name
                etConfig.setText(selected.content)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // ปุ่มต่าง ๆ
        findViewById<Button>(R.id.btnScanQR).setOnClickListener { startQRScan() }
        findViewById<Button>(R.id.btnImport).setOnClickListener { importFile() }
        findViewById<Button>(R.id.btnConnect).setOnClickListener { connectVPN() }
        findViewById<Button>(R.id.btnDisconnect).setOnClickListener { disconnectVPN() }
    }

    // ==================== SCAN QR CODE ====================
    private fun startQRScan() {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)

        // ใช้ CameraX หรือ Intent ง่าย ๆ (เวอร์ชันง่าย)
        Toast.makeText(this, "ฟีเจอร์ Scan QR กำลังพัฒนา (ใช้ Import .conf ชั่วคราว)", Toast.LENGTH_LONG).show()
        // ถ้าต้องการเวอร์ชันเต็ม สามารถใช้ ML Kit + CameraX ได้
    }

    // ==================== IMPORT FILE ====================
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { readConfigFile(it) }
    }

    private fun importFile() {
        importLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
    }

    private fun readConfigFile(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val content = inputStream?.bufferedReader()?.readText() ?: ""
            
            if (content.contains("[Interface]") && content.contains("PrivateKey")) {
                val fileName = "Config-${System.currentTimeMillis()}"
                configList.add(WireGuardConfig(fileName, content))
                refreshSpinner()
                
                etConfig.setText(content)
                Toast.makeText(this, "✅ Import สำเร็จ: $fileName", Toast.LENGTH_SHORT).show()
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

    // ==================== CONNECT ====================
    private fun connectVPN() {
        val configText = etConfig.text.toString().trim()
        if (configText.isEmpty()) {
            Toast.makeText(this, "กรุณาใส่ Config ก่อน", Toast.LENGTH_SHORT).show()
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
                val errorMsg = e.message ?: e.toString()
                withContext(Dispatchers.Main) {
                    tvStatus.text = "❌ Error: $errorMsg"
                    Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
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