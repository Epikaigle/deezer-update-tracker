// Adapted from APKUpdater 3.x (https://github.com/rumboalla/apkupdater)
// Original project copyright rumboalla and contributors; GNU GPL-3.0.
package io.github.epikaigle.deezertracker.network.play

import com.aurora.gplayapi.network.IHttpClient


interface IProxyHttpClient : IHttpClient {
    @Throws(UnsupportedOperationException::class)
    fun setProxy(proxyInfo: ProxyInfo): IHttpClient
}
