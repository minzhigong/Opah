package com.opah.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "build_log")
public class BuildLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "build_id")
    private Long buildId;

    @Column(name = "line_no")
    private Integer lineNo;

    private String content;

    @Column(name = "is_stderr")
    private Integer isStderr;

    @Column(name = "created_at")
    private String createdAt;

    public BuildLogEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBuildId() { return buildId; }
    public void setBuildId(Long buildId) { this.buildId = buildId; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getIsStderr() { return isStderr; }
    public void setIsStderr(Integer isStderr) { this.isStderr = isStderr; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
