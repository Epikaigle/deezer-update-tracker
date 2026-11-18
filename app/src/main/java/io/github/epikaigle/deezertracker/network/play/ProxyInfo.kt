// Adapted from APKUpdater 3.x (https://github.com/rumboalla/apkupdater)
// Original project copyright rumboalla and contributors; GNU GPL-3.0.
package io.github.epikaigle.deezertracker.network.play


data class ProxyInfo(
    var protocol: String,
    var host: String,
    var port: Int,
    var proxyUser: String?,
    var proxyPassword: String?
)
