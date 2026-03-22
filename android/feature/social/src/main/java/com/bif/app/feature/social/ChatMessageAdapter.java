package com.bif.app.feature.social;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public enum MessageType {
        TEXT,
        LOCATION,
        EVENT
    }

    public static class ChatMessage {
        private final String sender;
        private final String title;
        private final String subtitle;
        private final String linkText;
        private final String time;
        private final boolean mine;
        private final MessageType type;

        public ChatMessage(String sender, String title, String subtitle, String linkText, String time, boolean mine, MessageType type) {
            this.sender = sender;
            this.title = title;
            this.subtitle = subtitle;
            this.linkText = linkText;
            this.time = time;
            this.mine = mine;
            this.type = type;
        }

        public String getSender() {
            return sender;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public String getLinkText() {
            return linkText;
        }

        public String getTime() {
            return time;
        }

        public boolean isMine() {
            return mine;
        }

        public MessageType getType() {
            return type;
        }
    }

    private static final int VIEW_TYPE_INCOMING = 1;
    private static final int VIEW_TYPE_OUTGOING = 2;

    private final List<ChatMessage> messages = new ArrayList<>();

    public void submit(List<ChatMessage> newMessages) {
        messages.clear();
        if (newMessages != null) {
            messages.addAll(newMessages);
        }
        notifyDataSetChanged();
    }

    public void add(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isMine() ? VIEW_TYPE_OUTGOING : VIEW_TYPE_INCOMING;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == VIEW_TYPE_OUTGOING
                ? R.layout.item_chat_outgoing
                : R.layout.item_chat_incoming;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ChatMessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ((ChatMessageViewHolder) holder).bind(messages.get(position));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvSender;
        private final TextView tvTitle;
        private final TextView tvSubtitle;
        private final TextView tvLink;
        private final TextView tvTime;

        ChatMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tv_sender);
            tvTitle = itemView.findViewById(R.id.tv_message_title);
            tvSubtitle = itemView.findViewById(R.id.tv_message_subtitle);
            tvLink = itemView.findViewById(R.id.tv_message_link);
            tvTime = itemView.findViewById(R.id.tv_message_time);
        }

        void bind(ChatMessage message) {
            if (message.isMine()) {
                tvSender.setVisibility(View.GONE);
            } else {
                tvSender.setVisibility(View.VISIBLE);
                tvSender.setText(message.getSender());
            }

            tvTitle.setText(message.getTitle());

            if (message.getSubtitle() == null || message.getSubtitle().isEmpty()) {
                tvSubtitle.setVisibility(View.GONE);
            } else {
                tvSubtitle.setVisibility(View.VISIBLE);
                tvSubtitle.setText(message.getSubtitle());
            }

            if (message.getLinkText() == null || message.getLinkText().isEmpty()) {
                tvLink.setVisibility(View.GONE);
            } else {
                tvLink.setVisibility(View.VISIBLE);
                tvLink.setText(message.getLinkText());
            }

            tvTime.setText(message.getTime());
        }
    }
}
