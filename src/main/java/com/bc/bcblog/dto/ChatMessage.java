package com.bc.bcblog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一条对话消息（role + content）。
 *
 * 用途：把「系统提示 / 用户提示 / assistant 预填充」按顺序交给模型。
 * 预填充（最后一条是 assistant）能让模型"接着写"，是保证输出格式的利器。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    /** system / user / assistant */
    private String role;
    private String content;

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}
