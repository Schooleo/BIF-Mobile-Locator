package com.bif.app.feature.social;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.bif.app.core.utils.UriUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CommonChatFragment extends Fragment {

    private ChatMessageAdapter adapter;
    private String chatType;
    private EditText messageInput;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_common_chat, container, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        chatType = getArg(args, "chatType", "friend");
        String chatId = getArg(args, "chatId", "");
        String chatName = getArg(args, "chatName", getString(R.string.chat_default_name));
        String avatarLetter = getArg(args, "avatarLetter", "?");
        int avatarColor = args != null ? args.getInt("avatarColor", 0) : 0;
        int memberCount = args != null ? args.getInt("memberCount", 0) : 0;

        TextView tvAvatar = view.findViewById(R.id.tv_avatar);
        TextView tvTitle = view.findViewById(R.id.tv_chat_title);
        TextView tvSubtitle = view.findViewById(R.id.tv_chat_subtitle);
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        ImageButton btnGroupSettings = view.findViewById(R.id.btn_group_settings);
        RecyclerView rvMessages = view.findViewById(R.id.rv_messages);
        EditText etMessage = view.findViewById(R.id.et_message);
        messageInput = etMessage;
        MaterialButton btnSend = view.findViewById(R.id.btn_send);

        tvAvatar.setText(avatarLetter);
        if (avatarColor != 0) {
            tvAvatar.setBackgroundTintList(ColorStateList.valueOf(avatarColor));
        }

        tvTitle.setText(chatName);
        if ("group".equalsIgnoreCase(chatType)) {
            tvSubtitle.setText(getString(R.string.chat_member_count, Math.max(memberCount, 1)));
            btnGroupSettings.setVisibility(View.VISIBLE);
            btnGroupSettings.setOnClickListener(v -> {
                android.net.Uri settingsUri = UriUtils.buildUri(UriUtils.PathTo.GROUP_SETTINGS_PLANS)
                        .buildUpon()
                        .appendQueryParameter("groupId", chatId)
                        .build();
                Navigation.findNavController(view).navigate(settingsUri);
            });
        } else {
            tvSubtitle.setText(R.string.chat_direct_subtitle);
            btnGroupSettings.setVisibility(View.VISIBLE);
            btnGroupSettings.setOnClickListener(v -> {
                android.net.Uri settingsUri = UriUtils.buildUri(UriUtils.PathTo.FRIEND_SETTINGS_LOCATIONS)
                        .buildUpon()
                        .appendQueryParameter("friendId", chatId)
                        .appendQueryParameter("friendName", chatName)
                        .appendQueryParameter("avatarLetter", avatarLetter)
                        .appendQueryParameter("avatarColor", String.valueOf(avatarColor))
                        .build();
                Navigation.findNavController(view).navigate(settingsUri);
            });
        }

        btnBack.setOnClickListener(v -> navigateBackFromChat(view));

        // Tap blank space on the chat screen to focus the input.
        view.setOnClickListener(v -> focusInputAndShowKeyboard(etMessage));

        // Always open keyboard when entering the chat screen.
        etMessage.post(() -> focusInputAndShowKeyboard(etMessage));

        adapter = new ChatMessageAdapter(this::handleLocationLinkClick);
        rvMessages.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMessages.setAdapter(adapter);
        adapter.submit(buildSeedMessages(chatType));

        if (savedInstanceState == null) {
            appendSharedPlaceMessageIfPresent(args);
        }

        rvMessages.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));

        // Tap on the chat area to type immediately instead of triggering any navigation.
        rvMessages.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                focusInputAndShowKeyboard(etMessage);
                v.performClick();
            }
            return false;
        });

        btnSend.setOnClickListener(v -> {
            String input = etMessage.getText().toString().trim();
            if (input.isEmpty()) {
                return;
            }

            adapter.add(new ChatMessageAdapter.ChatMessage(
                    getString(R.string.chat_you),
                    input,
                    "",
                    "",
                    nowTime(),
                    true,
                    ChatMessageAdapter.MessageType.TEXT
            ));

            etMessage.setText("");
            rvMessages.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
            focusInputAndShowKeyboard(etMessage);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (messageInput != null) {
            messageInput.post(() -> focusInputAndShowKeyboard(messageInput));
        }
    }

    private List<ChatMessageAdapter.ChatMessage> buildSeedMessages(String type) {
        List<ChatMessageAdapter.ChatMessage> list = new ArrayList<>();

        if ("group".equalsIgnoreCase(type)) {
            list.add(new ChatMessageAdapter.ChatMessage(
                    "Alice",
                    getString(R.string.chat_seed_group_1),
                    "",
                    "",
                    "10:30",
                    false,
                    ChatMessageAdapter.MessageType.TEXT
            ));
            list.add(new ChatMessageAdapter.ChatMessage(
                    getString(R.string.chat_you),
                    getString(R.string.chat_seed_group_2),
                    "",
                    "",
                    "10:32",
                    true,
                    ChatMessageAdapter.MessageType.TEXT
            ));
            list.add(new ChatMessageAdapter.ChatMessage(
                    "Bob",
                    getString(R.string.chat_seed_group_3),
                    getString(R.string.chat_seed_group_4),
                    getString(R.string.chat_seed_group_5),
                    "10:35",
                    false,
                    ChatMessageAdapter.MessageType.LOCATION
            ));
        } else {
            list.add(new ChatMessageAdapter.ChatMessage(
                    getString(R.string.chat_friend_name),
                    getString(R.string.chat_seed_friend_1),
                    "",
                    "",
                    "09:48",
                    false,
                    ChatMessageAdapter.MessageType.TEXT
            ));
            list.add(new ChatMessageAdapter.ChatMessage(
                    getString(R.string.chat_you),
                    getString(R.string.chat_seed_friend_2),
                    "",
                    "",
                    "09:50",
                    true,
                    ChatMessageAdapter.MessageType.TEXT
            ));
        }

        return list;
    }

    private String nowTime() {
        return DateFormat.format("HH:mm", new Date()).toString();
    }

    private void appendSharedPlaceMessageIfPresent(Bundle args) {
        if (args == null || !"group".equalsIgnoreCase(chatType)) {
            return;
        }

        String placeName = getArg(args, "sharedPlaceName", "");
        if (placeName.isEmpty()) {
            return;
        }

        String placeAddress = getArg(args, "sharedPlaceAddress", "");
        String mapLink = getArg(args, "sharedPlaceLink", "");

        adapter.add(new ChatMessageAdapter.ChatMessage(
                getString(R.string.chat_you),
                placeName,
                placeAddress,
                getString(R.string.chat_seed_group_5),
                mapLink,
                nowTime(),
                true,
                ChatMessageAdapter.MessageType.LOCATION
        ));
    }

    private void handleLocationLinkClick(ChatMessageAdapter.ChatMessage message) {
        String mapQuery = message.getMapQuery();
        if (mapQuery == null || mapQuery.trim().isEmpty()) {
            if (message.getSubtitle() != null && !message.getSubtitle().trim().isEmpty()) {
                mapQuery = message.getSubtitle();
            } else {
                mapQuery = message.getTitle();
            }
        }

        Bundle args = new Bundle();
        args.putString("location", mapQuery);
        // Using deep link navigation for map to avoid direct resource dependency across modules
        android.net.Uri mapUri = UriUtils.buildUri("/map")
                .buildUpon()
                .appendQueryParameter("location", mapQuery)
                .build();
        Navigation.findNavController(requireView()).navigate(mapUri);
    }

    private String getArg(Bundle args, String key, String fallback) {
        if (args == null) {
            return fallback;
        }
        String value = args.getString(key);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value;
    }

    private void navigateBackFromChat(View rootView) {
        if ("group".equalsIgnoreCase(chatType)) {
            // Ask Social screen to open the Groups tab after navigation.
            getParentFragmentManager().setFragmentResult("groupDetailResult", new Bundle());
        }

        android.net.Uri socialUri = UriUtils.buildUri(UriUtils.PathTo.SOCIAL);
        Navigation.findNavController(rootView).navigate(socialUri);
    }

    private void focusInputAndShowKeyboard(EditText etMessage) {
        etMessage.requestFocus();
        etMessage.setFocusableInTouchMode(true);

        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(etMessage);
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.ime());
        }

        InputMethodManager imm = requireContext().getSystemService(InputMethodManager.class);
        if (imm != null) {
            imm.showSoftInput(etMessage, InputMethodManager.SHOW_IMPLICIT);
        }

        // Retry once after layout pass for devices that ignore the first request.
        etMessage.postDelayed(() -> {
            if (isAdded()) {
                if (!etMessage.hasFocus()) {
                    etMessage.requestFocus();
                }
                WindowInsetsControllerCompat delayedController = ViewCompat.getWindowInsetsController(etMessage);
                if (delayedController != null) {
                    delayedController.show(WindowInsetsCompat.Type.ime());
                }
                if (imm != null) {
                    imm.showSoftInput(etMessage, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 120);
    }
}
