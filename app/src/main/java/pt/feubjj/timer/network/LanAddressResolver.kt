package pt.feubjj.timer.network

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Resolves the active local Wi-Fi or Ethernet IPv4 address of the Android TV / TV Box
 * so the Scoreboard screen displays a short, direct LAN URL (`192.168.x.x:7777`) for mobile phones.
 */
object LanAddressResolver {

    fun findLocalIpv4Address(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val candidates = mutableListOf<Pair<Int, String>>()

            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback || nif.isVirtual) continue
                val name = nif.name.lowercase()
                val priority = when {
                    name.startsWith("wlan") || name.startsWith("en") -> 0
                    name.startsWith("eth") -> 1
                    name.startsWith("tun") || name.startsWith("rmnet") || name.startsWith("dummy") -> 9
                    else -> 2
                }
                if (priority >= 9) continue

                for (addr in nif.inetAddresses.toList()) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        val ip = addr.hostAddress ?: continue
                        candidates += priority to ip
                    }
                }
            }
            candidates.minByOrNull { it.first }?.second
        }.getOrNull()
    }
}
