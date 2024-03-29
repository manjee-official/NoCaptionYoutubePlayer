package com.manjee.nocaptionyoutubeplayer.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import java.lang.ref.WeakReference

/** Class used to observe changes to network state */
internal class NetworkObserver(private val context: Context) {

  interface Listener {
    fun onNetworkAvailable()
    fun onNetworkUnavailable()
  }

  val listeners = mutableListOf<Listener>()

  private var networkBroadcastReceiver: NetworkBroadcastReceiver? = null
  private var networkCallback: ConnectivityManager.NetworkCallback? = null

  /** Start observing network changes */
  fun observeNetwork() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      doObserveNetwork(context)
    } else {
      doObserveNetworkLegacy(context)
    }
  }

  /** Stop observing network changes and cleanup */
  fun destroy() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      // Check if networkCallback is not null before using it
      networkCallback?.let { callback ->
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(callback)
      }
    } else {
      val receiver = networkBroadcastReceiver ?: return
      runCatching { context.unregisterReceiver(receiver) }
    }

    listeners.clear()
    networkCallback = null
    networkBroadcastReceiver = null
  }

  @RequiresApi(Build.VERSION_CODES.N)
  private fun doObserveNetwork(context: Context) {
    val callback = object : ConnectivityManager.NetworkCallback() {
      private val mainThreadHandler = Handler(Looper.getMainLooper())

      override fun onAvailable(network: Network) {
        mainThreadHandler.post {
          listeners.forEach { it.onNetworkAvailable() }
        }
      }

      override fun onLost(network: Network) {
        mainThreadHandler.post {
          listeners.forEach { it.onNetworkUnavailable() }
        }
      }
    }

    // Register the callback
    networkCallback = callback
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    connectivityManager.registerDefaultNetworkCallback(callback)
  }


  private fun doObserveNetworkLegacy(context: Context) {
    networkBroadcastReceiver = NetworkBroadcastReceiver(
      onNetworkAvailable = { listeners.forEach { it.onNetworkAvailable() } },
      onNetworkUnavailable = { listeners.forEach { it.onNetworkUnavailable() } },
    )

    // Use a weak reference to avoid context leaks
    val weakContext = WeakReference(context)
    networkBroadcastReceiver?.let { receiver ->
      weakContext.get()?.registerReceiver(receiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }
  }
}

/** Broadcast receiver used to react to changes in internet connectivity */
private class NetworkBroadcastReceiver(
  private val onNetworkAvailable: () -> Unit,
  private val onNetworkUnavailable: () -> Unit
) : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    if (isConnectedToInternet(context)) {
      onNetworkAvailable()
    } else {
      onNetworkUnavailable()
    }
  }
}

private fun isConnectedToInternet(context: Context): Boolean {
  val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork) ?: return false
    networkCapabilities.isConnectedToInternet()
  } else {
    val networkInfo = connectivityManager.activeNetworkInfo
    networkInfo != null && networkInfo.isConnected
  }
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
private fun NetworkCapabilities.isConnectedToInternet(): Boolean {
  return (hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
          hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
          hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
}
