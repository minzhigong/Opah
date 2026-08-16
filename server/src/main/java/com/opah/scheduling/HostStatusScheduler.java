package com.opah.scheduling;

import com.opah.domain.HostRepository;
import com.opah.service.HostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 主机在线状态轮询（HOST-05，15s 间隔） */
@Component
public class HostStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(HostStatusScheduler.class);

    private final HostRepository hosts;
    private final HostService hostService;

    public HostStatusScheduler(HostRepository hosts, HostService hostService) {
        this.hosts = hosts;
        this.hostService = hostService;
    }

    @Scheduled(fixedDelay = 15_000)
    public void poll() {
        hosts.findAll().forEach(host -> {
            try {
                hostService.refreshStatus(host.getId());
            } catch (Exception e) {
                log.debug("poll host {} failed: {}", host.getName(), e.getMessage());
            }
        });
    }
}
