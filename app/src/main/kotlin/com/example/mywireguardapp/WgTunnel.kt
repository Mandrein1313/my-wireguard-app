package com.example.mywireguardapp

import com.wireguard.android.backend.Tunnel

class WgTunnel(private val name: String) : Tunnel {

    override fun getName(): String = name

    override fun onStateChange(newState: Tunnel.State) {
        // สามารถ log หรืออัพเดท UI ได้ที่นี่
        println("WireGuard state changed to: $newState")
    }
}