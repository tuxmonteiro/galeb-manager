/*
 *   Galeb - Load Balance as a Service Plataform
 *
 *   Copyright (C) 2014-2015 Globo.com
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.galeb.manager.entity;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.cache.Cache;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Transient;
import javax.persistence.Version;

import io.galeb.core.cluster.ignite.IgniteCacheFactory;
import io.galeb.core.jcache.CacheFactory;
import io.galeb.core.json.JsonObject;
import io.galeb.core.model.Entity;
import io.galeb.manager.common.JsonCustomProperties;
import io.galeb.manager.engine.listeners.AbstractEngine;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.util.Assert;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.galeb.manager.security.config.SpringSecurityAuditorAware;

@MappedSuperclass
@JsonCustomProperties
public abstract class AbstractEntity<T extends AbstractEntity<?>> implements Serializable {

    private static final long serialVersionUID = 4521414292400791447L;

    protected static final CacheFactory CACHE_FACTORY = IgniteCacheFactory.getInstance().start();

    public enum EntityStatus {
        PENDING,
        OK,
        ERROR,
        UNKNOWN,
        DISABLED,
        ENABLE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Version
    @Column(name = "_version")
    @JsonProperty("_version")
    private Long version;

    @CreatedBy
    @Column(name = "_created_by", nullable = false, updatable = false)
    @JsonProperty("_created_by")
    private String createdBy;

    @CreatedDate
    @Column(name = "_created_at", nullable = false, updatable = false)
    @JsonProperty("_created_at")
    private Date createdAt;

    @LastModifiedDate
    @Column(name = "_lastmodified_at", nullable = false)
    @JsonProperty("_lastmodified_at")
    private Date lastModifiedAt;

    @LastModifiedBy
    @Column(name = "_lastmodified_by", nullable = false)
    @JsonProperty("_lastmodified_by")
    private String lastModifiedBy;

    @Column(name = "name", nullable = false)
    @JsonProperty(required = true)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @JoinColumn(nullable = false)
    private final Map<String, String> properties = new HashMap<>();

    @Column(name = "_status", nullable = false)
    @JsonProperty("_status")
    protected EntityStatus status;

    @JsonIgnore
    @Transient
    private boolean saveOnly = false;

    private String description;

    private Integer hash = 0;

    @PrePersist
    private void onCreate() {
        createdAt = new Date();
        createdBy = getCurrentAuditor();
        lastModifiedAt = createdAt;
        lastModifiedBy = createdBy;
        saveOnly = false;
    }

    @PreUpdate
    private void onUpdate() {
        lastModifiedAt = new Date();
        lastModifiedBy = getCurrentAuditor();
    }

    private String getCurrentAuditor() {
        final SpringSecurityAuditorAware auditorAware = new SpringSecurityAuditorAware();
        return auditorAware.getCurrentAuditor();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        updateHash();
        this.id = id;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public Date getLastModifiedAt() {
        return lastModifiedAt;
    }

    public Long getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    @SuppressWarnings("unchecked")
    public T setName(String name) {
        Assert.hasText(name);
        updateHash();
        this.name = name;
        return (T) this;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    @SuppressWarnings("unchecked")
    public T setProperties(Map<String, String> properties) {
        if (properties != null) {
            updateHash();
            this.properties.clear();
            this.properties.putAll(properties);
        }
        return (T) this;
    }

    public EntityStatus getStatus() {
        Cache<String, String> distMap = CACHE_FACTORY.getCache(this.getClass().getSimpleName());
        String key = getName() + AbstractEngine.SEPARATOR;
        String json = distMap.get(key);
        if (json != null) {
            Entity entity = (Entity) JsonObject.fromJson(json, Entity.class);
            if (entity.getVersion() == getHash()) {
                return EntityStatus.OK;
            }
        }
        return status;
    }

    @SuppressWarnings("unchecked")
    public T setStatus(EntityStatus aStatus) {
        status = Optional.ofNullable(aStatus).orElse(status);
        return (T) this;
    }

    public boolean isSaveOnly() {
        return saveOnly;
    }

    @SuppressWarnings("unchecked")
    public T setSaveOnly(boolean saveOnly) {
        this.saveOnly = saveOnly;
        return (T) this;
    }

    @Override
    public int hashCode() {
        if (name != null) {
            return name.hashCode();
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        T other = (T) obj;
        return other.getName() != null && other.getName().equals(getName());
    }

    public String getDescription() {
        return description;
    }

    @SuppressWarnings("unchecked")
    public T setDescription(String description) {
        this.description = description;
        return (T) this;
    }

    public int getHash() {
        return hash;
    }

    @SuppressWarnings("unchecked")
    public T setHash(Integer hash) {
        if (hash != null) {
            this.hash = hash;
        } else {
            if (this.hash == null) {
                this.hash = 0;
            }
        }
        return (T) this;
    }

    @JsonIgnore
    public void updateHash() {
        if (hash != null && hash < Integer.MAX_VALUE) {
            hash++;
        } else {
            hash = 0;
        }
    }

}
