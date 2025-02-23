package com.plexpt.chatgpt.listener;

import com.plexpt.chatgpt.util.SseHelper;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * sse
 *
 * @author plexpt
 */
@Slf4j
@RequiredArgsConstructor
public class SseStreamListener extends AbstractStreamListener {

    final SseEmitter sseEmitter;


    @Override
    public void onMsg(String msg) {
        SseHelper.send(sseEmitter, msg);
    }

    @Override
    public void onError(Throwable t, String response) {
        SseHelper.complete(sseEmitter);
    }

}
