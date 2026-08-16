package com.opah.service;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

/** 构建模板引擎（BUILD-01）：Handlebars 渲染 Dockerfile / nginx.conf */
@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);
    private final Handlebars handlebars = new Handlebars();

    /** 渲染 Dockerfile / nginx.conf 并写入指定路径 */
    public Path renderToFile(String templateName, Map<String, Object> params, Path target) {
        try {
            String tplContent = readTemplate(templateName);
            Template template = handlebars.compileInline(tplContent);
            String rendered = template.apply(params);
            Files.createDirectories(target.getParent());
            Files.writeString(target, rendered, StandardCharsets.UTF_8);
            log.debug("rendered {} -> {}", templateName, target);
            return target;
        } catch (Exception e) {
            throw new IllegalStateException("模板渲染失败: " + e.getMessage(), e);
        }
    }

    private String readTemplate(String name) throws Exception {
        try (InputStream in = new ClassPathResource("templates/" + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
