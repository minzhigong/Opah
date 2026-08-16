package com.opah.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "config_entry")
public class ConfigEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_id")
    private Long serviceId;

    private String key;

    @Column(name = "value_cipher")
    private String valueCipher;

    @Column(name = "is_sensitive")
    private Integer isSensitive;

    @Column(name = "updated_at")
    private String updatedAt;

    public ConfigEntryEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValueCipher() { return valueCipher; }
    public void setValueCipher(String valueCipher) { this.valueCipher = valueCipher; }
    public Integer getIsSensitive() { return isSensitive; }
    public void setIsSensitive(Integer isSensitive) { this.isSensitive = isSensitive; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
