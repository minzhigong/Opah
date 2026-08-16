package com.opah.api.ws;

import com.opah.domain.BuildEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** 构建日志/状态 WebSocket 推送（/topic/builds/{id}） */
@Component
public class BuildLogPublisher {

    private static final Logger log = LoggerFactory.getLogger(BuildLogPublisher.class);

    private final SimpMessagingTemplate template;

    public BuildLogPublisher(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void publishLog(Long buildId, int lineNo, String line) {
        try {
            template.convertAndSend("/topic/builds/" + buildId,
                    java.util.Map.of("type", "log", "lineNo", lineNo, "content", line));
        } catch (Exception e) {
            log.debug("ws log push failed: {}", e.getMessage());
        }
    }

    public void publishState(BuildEntity b) {
        try {
            template.convertAndSend("/topic/builds/" + b.getId(),
                    java.util.Map.of("type", "state",
                            "status", b.getStatus(),
                            "versionTag", b.getVersionTag() == null ? "" : b.getVersionTag()));
        } catch (Exception e) {
            log.debug("ws state push failed: {}", e.getMessage());
        }
    }
}
