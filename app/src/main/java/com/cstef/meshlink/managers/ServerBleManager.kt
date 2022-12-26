package com.cstef.meshlink.managers

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Parcel
import android.os.ParcelUuid
import android.util.Base64
import android.util.Log
import com.cstef.meshlink.util.BleUuid
import com.cstef.meshlink.util.struct.Chunk
import com.cstef.meshlink.util.struct.KeyData
import com.cstef.meshlink.util.struct.WritingData
import com.daveanthonythomas.moshipack.MoshiPack
import java.util.*

class ServerBleManager(
  private val context: Context,
  private val dataExchangeManager: BleManager.BleDataExchangeManager,
  private val callbackHandler: Handler,
  private val encryptionManager: EncryptionManager,
  private val parentManager: BleManager
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
  private val userPublicKeyCharacteristic = BluetoothGattCharacteristic(
    UUID.fromString(BleUuid.USER_PUBLIC_KEY_UUID),
    BluetoothGattCharacteristic.PROPERTY_READ,
    BluetoothGattCharacteristic.PERMISSION_READ
  )
//  private val userWritingCharacteristic = BluetoothGattCharacteristic(
//    UUID.fromString(BleUuid.USER_WRITING_UUID),
//    BluetoothGattCharacteristic.PROPERTY_WRITE,
//    BluetoothGattCharacteristic.PERMISSION_WRITE
//  )

  private val bleService = BluetoothGattService(
    UUID.fromString(BleUuid.SERVICE_UUID), BluetoothGattService.SERVICE_TYPE_PRIMARY
  ).apply {
    addCharacteristic(writeCharacteristic)
    addCharacteristic(userIdCharacteristic)
    addCharacteristic(userPublicKeyCharacteristic)
//    addCharacteristic(userWritingCharacteristic)
  }

  private val advertiseSettings =
    AdvertiseSettings.Builder()
      .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
      .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
      .setTimeout(0)
      .setConnectable(true)
      .build()

  private val advertiseCallback = object : AdvertiseCallback() {
    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
      super.onStartSuccess(settingsInEffect)
      Log.d("ServerBleManager", "Advertising start success")
    }

    override fun onStartFailure(errorCode: Int) {
      super.onStartFailure(errorCode)
      Log.d("ServerBleManager", "Advertising start failure: $errorCode")
    }
  }

  private val serverCallback = object : BluetoothGattServerCallback() {
    @SuppressLint("MissingPermission")
    override fun onCharacteristicReadRequest(
      device: BluetoothDevice?,
      requestId: Int,
      offset: Int,
      characteristic: BluetoothGattCharacteristic?
    ) {
      super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
      Log.d("ServerBleManager", "onCharacteristicReadRequest: from ${device?.address}")
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
      when (characteristic?.uuid.toString()) {
        BleUuid.WRITE_UUID -> {
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
        BleUuid.USER_WRITING_UUID -> {
          val writingData = moshi.unpack<WritingData>(value!!)
          Log.d("ServerBleManager", "onCharacteristicWriteRequest: $writingData")
          if (userId != null) {
            callbackHandler.post {
              dataExchangeManager.onUserWriting(
                writingData.userId,
                writingData.isWriting
              )
            }
          }
        }
      }
    }

    override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
      super.onMtuChanged(device, mtu)
      Log.d("ServerBleManager", "onMtuChanged: $mtu")
    }

    override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
      super.onConnectionStateChange(device, status, newState)
      Log.d("ServerBleManager", "onConnectionStateChange: $status, $newState")
      if (newState == BluetoothProfile.STATE_CONNECTED) {
        // try to connect back
        callbackHandler.post {
          device?.address?.let { dataExchangeManager.connect(it) }
        }
      } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        callbackHandler.post {
          dataExchangeManager.onUserDisconnected(device?.address!!)
        }
      }
    }
  }

  @SuppressLint("MissingPermission")
  fun openServer() {
    if (adapter.isBleOn && gattServer == null) {
      gattServer = bluetoothManager.openGattServer(context, serverCallback)
      gattServer?.addService(bleService)
    }
  }

  @SuppressLint("MissingPermission")
  fun closeServer() {
    gattServer?.clearServices()
    gattServer?.close()
    gattServer = null
  }

  @SuppressLint("MissingPermission")
  fun startAdvertising() {
    if (adapter.isBleOn) {
      val userIdBytes = userId?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
      val advertiseData =
        AdvertiseData.Builder()
          .addServiceUuid(ParcelUuid.fromString(BleUuid.SERVICE_UUID))
          .setIncludeDeviceName(false)
          .setIncludeTxPowerLevel(false)
          .build()
      Log.d(
        "ServerBleManager",
        "startAdvertising: userId size (bytes): ${userIdBytes.size}, data size: ${
          getAdvertisingDataSize(
            advertiseData
          )
        }, max: ${adapter?.leMaximumAdvertisingDataLength}"
      )
      advertiser?.startAdvertising(
        advertiseSettings, advertiseData, advertiseCallback
      )
      parentManager.isAdvertising.value = true
      Log.d("ServerBleManager", "startAdvertising: started")
    }
  }

  @SuppressLint("MissingPermission")
  fun stopAdvertising() {
    if (adapter.isBleOn) {
      advertiser?.stopAdvertising(advertiseCallback)
      parentManager.isAdvertising.value = false
      Log.d("ServerBleManager", "stopAdvertising: stopped")
    }
  }

  fun setUserId(id: String) {
    userId = id
  }

  fun getAdvertisingDataSize(data: AdvertiseData): Int {
    val parcel = Parcel.obtain()
    data.writeToParcel(parcel, 0)
    val bytes = parcel.marshall()
    parcel.recycle()
    return bytes.size
  }
}
