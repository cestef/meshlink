package com.cstef.meshlink.managers

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.ParcelUuid
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import com.cstef.meshlink.util.BleUuid
import com.cstef.meshlink.util.struct.Chunk
import com.cstef.meshlink.util.struct.KeyData
import com.daveanthonythomas.moshipack.MoshiPack
import java.util.*

class ServerBleManager(
  private val context: Context,
  private val dataExchangeManager: BleManager.BleDataExchangeManager,
  private val callbackHandler: Handler,
  private val encryptionManager: EncryptionManager
) {

  private val moshi = MoshiPack()
  private var userId: String? = null
  private val bluetoothManager =
    context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
  private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter
  private val advertiser get() = adapter?.bluetoothLeAdvertiser

  var gattServer: BluetoothGattServer? = null

  private val writeCharacteristic = BluetoothGattCharacteristic(
    UUID.fromString(BleUuid.WRITE_UUID),
    BluetoothGattCharacteristic.PROPERTY_WRITE,
    BluetoothGattCharacteristic.PERMISSION_WRITE
  )
  private val userIdCharacteristic = BluetoothGattCharacteristic(
    UUID.fromString(BleUuid.USER_ID_UUID),
    BluetoothGattCharacteristic.PROPERTY_READ,
    BluetoothGattCharacteristic.PERMISSION_READ
  )
  private val userNameCharacteristic = BluetoothGattCharacteristic(
    UUID.fromString(BleUuid.USER_NAME_UUID),
    BluetoothGattCharacteristic.PROPERTY_READ,
    BluetoothGattCharacteristic.PERMISSION_READ
  )
  private val userPublicKeyCharacteristic = BluetoothGattCharacteristic(
    UUID.fromString(BleUuid.USER_PUBLIC_KEY_UUID),
    BluetoothGattCharacteristic.PROPERTY_READ,
    BluetoothGattCharacteristic.PERMISSION_READ
  )

  private val bleService = BluetoothGattService(
    UUID.fromString(BleUuid.SERVICE_UUID), BluetoothGattService.SERVICE_TYPE_PRIMARY
  ).apply {
    addCharacteristic(writeCharacteristic)
    addCharacteristic(userIdCharacteristic)
    addCharacteristic(userNameCharacteristic)
    addCharacteristic(userPublicKeyCharacteristic)
  }

  private val advertiseSettings =
    AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
      .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH).setTimeout(0).setConnectable(true)
      .build()

  private val advertiseData =
    AdvertiseData.Builder().addServiceUuid(ParcelUuid.fromString(BleUuid.SERVICE_UUID)).build()

  private val advertiseCallback = object : AdvertiseCallback() {}

  private val serverCallback = object : BluetoothGattServerCallback() {

    @SuppressLint("MissingPermission")
    override fun onCharacteristicReadRequest(
      device: BluetoothDevice?,
      requestId: Int,
      offset: Int,
      characteristic: BluetoothGattCharacteristic?
    ) {
      super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
      when (characteristic?.uuid.toString()) {
        BleUuid.USER_ID_UUID -> {
          callbackHandler.post {
            val data = userId?.toByteArray(Charsets.UTF_8)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, data)
          }
        }
        BleUuid.USER_NAME_UUID -> {
          callbackHandler.post {
            val data = dataExchangeManager.getUsername().toByteArray(Charsets.UTF_8)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, data)
          }
        }
        BleUuid.USER_PUBLIC_KEY_UUID -> {
          callbackHandler.post {
            if (userId != null) {
              val data = moshi.packToByteArray(
                KeyData(
                  Base64.encodeToString(encryptionManager.publicKey.encoded, Base64.DEFAULT),
                  userId!!
                )
              )
              gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, data)
            } else {
              gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
            }
          }
        }
      }
    }

    @SuppressLint("MissingPermission")
    override fun onCharacteristicWriteRequest(
      device: BluetoothDevice?,
      requestId: Int,
      characteristic: BluetoothGattCharacteristic?,
      preparedWrite: Boolean,
      responseNeeded: Boolean,
      offset: Int,
      value: ByteArray?
    ) {
      super.onCharacteristicWriteRequest(
        device, requestId, characteristic, preparedWrite, responseNeeded, offset, value
      )
      callbackHandler.post {
        try {
          val chunk = value?.let { Chunk.fromByteArray(it) }
          device?.address?.let {
            if (chunk != null) {
              dataExchangeManager.onChunkReceived(chunk, it)
            }
          }
          if (responseNeeded) {
            gattServer?.sendResponse(
              device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null
            )
          }
        } catch (e: Exception) {
          Log.e("ServerBleManager", "onCharacteristicWriteRequest: ", e)
          if (responseNeeded) {
            gattServer?.sendResponse(
              device, requestId, BluetoothGatt.GATT_FAILURE, offset, null
            )
          }
        }

      }
    }
  }

  fun start(myUserId: String) {
    if (ActivityCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_CONNECT
      ) == PackageManager.PERMISSION_GRANTED || (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_ADMIN
      ) == PackageManager.PERMISSION_GRANTED)
    ) {
      userId = myUserId
      openServer()
      startAdvertising()
    }
  }

  fun stop() {
    if ((ActivityCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_CONNECT
      ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_ADVERTISE
      ) == PackageManager.PERMISSION_GRANTED) || (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_ADMIN
      ) == PackageManager.PERMISSION_GRANTED)
    ) {
      Log.d("ServerBleManager", "Closing server")
      stopAdvertising()
      closeServer()
    }
  }

  @SuppressLint("MissingPermission")
  private fun openServer() {
    if (adapter.isBleOn && gattServer == null) {
      gattServer = bluetoothManager.openGattServer(context, serverCallback)
      gattServer?.addService(bleService)
    }
  }

  @SuppressLint("MissingPermission")
  private fun closeServer() {
    gattServer?.clearServices()
    gattServer?.close()
    gattServer = null
  }

  @SuppressLint("MissingPermission")
  private fun startAdvertising() {
    if (adapter.isBleOn) {
      advertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
    }
  }

  @SuppressLint("MissingPermission")
  private fun stopAdvertising() {
    if (adapter.isBleOn) {
      advertiser?.stopAdvertising(advertiseCallback)
    }
  }
}
