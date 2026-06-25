package com.example.mywireguardapp

import com.wireguard.android.backend.Tunnel

class WgTunnel(private val name: String) : Tunnel {

    override fun getName(): String = name

    override fun onStateChange(newState: Tunnel.State) {
        println("WireGuard state changed: $newState")
    }
}
