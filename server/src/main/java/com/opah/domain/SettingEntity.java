package com.opah.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 全局设置 KV（docker.host 等） */
@Entity
@Table(name = "setting")
public class SettingEntity {

    @Id
    private String key;

    @Column(name = "value")
    private String value;

    @Column(name = "updated_at")
    private String updatedAt;

    public SettingEntity() {
    }

    public SettingEntity(String key, String value, String updatedAt) {
        this.key = key;
        this.value = value;
        this.updatedAt = updatedAt;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
