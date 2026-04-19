package com.minisoc.lab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.minisoc.lab.model.GameAlert;
import com.minisoc.lab.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * SSE (Server-Sent Events) broadcaster.
 *
 * Maintains three emitter pools:
 *  - /api/stream/alerts  → pushed on every new GameAlert
 *  - /api/stream/logs    → pushed on every new log entry
 *  - /api/stream/command/{commandId} → pushed per stdout line from running commands
 */
@Service
public class StreamingService {

    private static final Logger log = LoggerFactory.getLogger(StreamingService.class);

    private final List<SseEmitter> alertEmitters = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> logEmitters   = new CopyOnWriteArrayList<>();
    private final Map<String, List<SseEmitter>> commandEmitters = new ConcurrentHashMap<>();

    private final ObjectMapper mapper;

    public StreamingService() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    // ===== Subscription =====

    public SseEmitter subscribeAlerts() {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        alertEmitters.add(emitter);
        emitter.onCompletion(() -> alertEmitters.remove(emitter));
        emitter.onTimeout(() -> alertEmitters.remove(emitter));
        emitter.onError(e -> alertEmitters.remove(emitter));
        return emitter;
    }

    public SseEmitter subscribeLogs() {
        SseEmitter emitter = new SseEmitter(0L);
        logEmitters.add(emitter);
        emitter.onCompletion(() -> logEmitters.remove(emitter));
        emitter.onTimeout(() -> logEmitters.remove(emitter));
        emitter.onError(e -> logEmitters.remove(emitter));
        return emitter;
    }

    public SseEmitter subscribeCommand(String commandId) {
        SseEmitter emitter = new SseEmitter(60_000L); // 1 minute for command output
        commandEmitters.computeIfAbsent(commandId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeCommandEmitter(commandId, emitter));
        emitter.onTimeout(() -> removeCommandEmitter(commandId, emitter));
        emitter.onError(e -> removeCommandEmitter(commandId, emitter));
        return emitter;
    }

    private void removeCommandEmitter(String commandId, SseEmitter emitter) {
        List<SseEmitter> list = commandEmitters.get(commandId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) commandEmitters.remove(commandId);
        }
    }

    // ===== Push methods =====

    public void pushAlert(GameAlert alert) {
        pushToAll(alertEmitters, "alert", alert);
    }

    public void pushAlertUpdate(GameAlert alert) {
        pushToAll(alertEmitters, "alert-updated", alert);
    }

    public void pushLog(LogEntry entry) {
        pushToAll(logEmitters, "log", entry);
    }

    public void pushCommandLine(String commandId, String line, String type) {
        List<SseEmitter> emitters = commandEmitters.get(commandId);
        if (emitters == null || emitters.isEmpty()) return;
        Map<String, String> payload = Map.of("commandId", commandId, "line", line, "type", type);
        pushToAll(emitters, "output", payload);
    }

    public void pushCommandComplete(String commandId, int exitCode, int points) {
        List<SseEmitter> emitters = commandEmitters.get(commandId);
        if (emitters == null || emitters.isEmpty()) return;
        Map<String, Object> payload = Map.of("commandId", commandId, "exitCode", exitCode, "points", points);
        pushToAll(emitters, "done", payload);
        // close all emitters for this command
        List<SseEmitter> copy = List.copyOf(emitters);
        commandEmitters.remove(commandId);
        for (SseEmitter e : copy) {
            try { e.complete(); } catch (Exception ignored) {}
        }
    }

    public void pushGameEvent(String eventType, Object payload) {
        pushToAll(alertEmitters, eventType, payload);
    }

    // ===== Internal =====

    private void pushToAll(List<SseEmitter> emitters, String eventName, Object data) {
        if (emitters.isEmpty()) return;
        String json;
        try {
            json = mapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("SSE serialize failed: {}", e.getMessage());
            return;
        }

        List<SseEmitter> dead = new java.util.ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(json));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }
}
