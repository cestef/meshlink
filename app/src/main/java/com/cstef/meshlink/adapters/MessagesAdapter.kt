package com.cstef.meshlink.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cstef.meshlink.R
import com.cstef.meshlink.chat.Message
import java.sql.Timestamp
import java.text.DateFormat

class MessagesAdapter(private val messages: MutableList<Message>) :
  RecyclerView.Adapter<RecyclerView.ViewHolder>() {
  private val VIEW_TYPE_MESSAGE_SENT = 1
  private val VIEW_TYPE_MESSAGE_RECEIVED = 2

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    if (viewType == VIEW_TYPE_MESSAGE_SENT) {
      return SentMessageViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_message_me, parent, false)
      )
    } else if (viewType == VIEW_TYPE_MESSAGE_RECEIVED) {
      return ReceivedMessageViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_message_other, parent, false)
      )
    }
    throw IllegalArgumentException("Invalid view type")
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    val message = messages[position]
    when (holder.itemViewType) {
      VIEW_TYPE_MESSAGE_SENT -> (holder as SentMessageViewHolder).bind(message)
      VIEW_TYPE_MESSAGE_RECEIVED -> (holder as ReceivedMessageViewHolder).bind(message)
    }
  }

  override fun getItemCount(): Int {
    return messages.size
  }

  fun updateData(messages: List<Message>) {
    this.messages.clear()
    this.messages.addAll(messages)
    notifyDataSetChanged()
  }

  override fun getItemViewType(position: Int): Int {
    return if (messages[position].isMe) {
      VIEW_TYPE_MESSAGE_SENT
    } else {
      VIEW_TYPE_MESSAGE_RECEIVED
    }
  }

  private class ReceivedMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val messageTextView: TextView = view.findViewById(R.id.tvMessage_other)
    private val timestampTextView: TextView = view.findViewById(R.id.tvTimestamp_other)
    private val dateTextView: TextView = view.findViewById(R.id.tvDate_other)

    fun bind(message: Message) {
      messageTextView.text = message.content
      timestampTextView.text =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Timestamp(message.timestamp))
      dateTextView.text =
        DateFormat.getDateInstance(DateFormat.SHORT).format(Timestamp(message.timestamp))
    }
  }

  private class SentMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val messageTextView: TextView = view.findViewById(R.id.tvMessage_me)
    private val timestampTextView: TextView = view.findViewById(R.id.tvTimestamp_me)
    private val dateTextView: TextView = view.findViewById(R.id.tvDate_me)

    fun bind(message: Message) {
      messageTextView.text = message.content
      timestampTextView.text =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Timestamp(message.timestamp))
      dateTextView.text =
        DateFormat.getDateInstance(DateFormat.SHORT).format(Timestamp(message.timestamp))
    }
  }
}
