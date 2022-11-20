package com.cstef.meshlink.chat

import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import com.cstef.meshlink.BleService
import com.cstef.meshlink.MainActivity
import com.cstef.meshlink.R
import com.cstef.meshlink.adapters.MessagesAdapter

class ChatFragment : Fragment() {
  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.fragment_chat, container, false)
  }

  private var rvMessages: androidx.recyclerview.widget.RecyclerView? = null
  private var deviceId: String? = null
  private lateinit var fileLauncher: ActivityResultLauncher<Intent>
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    deviceId = arguments?.getString("device_id")
    val tvDeviceId = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.tbChat)
    tvDeviceId.title = deviceId
    rvMessages = view.findViewById(R.id.rvMessages)
    rvMessages?.adapter = MessagesAdapter(mutableListOf())
    rvMessages?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
    (rvMessages?.adapter as MessagesAdapter).updateData(
      (requireActivity() as MainActivity).bleBinder?.getMessages(
        deviceId!!
      ) ?: mutableListOf()
    )
    val btnSend = view.findViewById<android.widget.Button>(R.id.btnSend)
    val etMessage = view.findViewById<android.widget.EditText>(R.id.etMessage)
    btnSend.setOnClickListener {
      val message = etMessage.text.toString()
      (requireActivity() as MainActivity).bleBinder?.sendMessage(deviceId!!, message)
      etMessage.text.clear()
    }
    val fabFile = view.findViewById<android.widget.ImageButton>(R.id.fabFile)
    fabFile.setOnClickListener {
      val intent = Intent(Intent.ACTION_GET_CONTENT)
      intent.type = "*/*"
      fileLauncher.launch(intent)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    fileLauncher = registerForActivityResult(
      androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
      if (result.resultCode == android.app.Activity.RESULT_OK) {
        // Encode the image to base64 and send it
        val uri = result.data?.data
        val inputStream = requireContext().contentResolver.openInputStream(uri!!)
        val bytes = inputStream?.readBytes()
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
        (requireActivity() as MainActivity).bleBinder?.sendMessage(deviceId!!, base64, "file")
      }
    }
  }

  override fun onResume() {
    super.onResume()
    requireActivity().registerReceiver(
      broadcastReceiver,
      IntentFilter(BleService.ACTION_MESSAGES.action)
    )
  }

  override fun onPause() {
    super.onPause()
    requireActivity().unregisterReceiver(broadcastReceiver)
  }

  private val broadcastReceiver = object : android.content.BroadcastReceiver() {
    @Suppress("UNCHECKED_CAST")
    override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
      when (intent?.action) {
        BleService.ACTION_MESSAGES.action -> {
          val messages = intent?.getSerializableExtra(BleService.EXTRA_MESSAGES)
          if (messages != null) {
            (messages as Map<*, *>)[deviceId!!]?.let {
              (rvMessages?.adapter as MessagesAdapter).updateData(
                it as List<Message>
              )
            }
          }
        }
      }
    }
  }
}
