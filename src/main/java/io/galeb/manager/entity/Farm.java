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

import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.springframework.util.Assert;

@Entity
@Table(uniqueConstraints = { @UniqueConstraint(name = "UK_name_farm", columnNames = { "name" }) })
public class Farm extends AbstractEntity<Farm> {

    private static final long serialVersionUID = 5596582746795373017L;

    @Column(nullable = false)
    private String domain;

    @Column(nullable = false)
    private String api;

    @ManyToOne
    @JoinColumn(name = "environment", nullable = false, foreignKey = @ForeignKey(name="FK_farm_environment"))
    private Environment environment;

    @ManyToOne
    @JoinColumn(name = "provider", nullable = false, foreignKey = @ForeignKey(name="FK_farm_provider"))
    private Provider provider;

    @Column
    private boolean autoReload = true;

    public Farm(String name,String domain, String api, Environment environment, Provider provider) {
        Assert.hasText(domain);
        Assert.hasText(api);
        Assert.notNull(environment);
        Assert.notNull(provider);
        setName(name);
        this.domain = domain;
        this.api = api;
        this.environment = environment;
        this.provider = provider;
    }

    protected Farm() {
        //
    }

    @Override
    @JoinColumn(foreignKey=@ForeignKey(name="FK_farm_properties"))
    public Map<String, String> getProperties() {
        return super.getProperties();
    }

    public String getDomain() {
        return domain;
    }

    public Farm setDomain(String domain) {
        this.domain = domain;
        return this;
    }

    public String getApi() {
        return api;
    }

    public Farm setApi(String api) {
        this.api = api;
        return this;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public Farm setEnvironment(Environment environment) {
        this.environment = environment;
        return this;
    }

    public Provider getProvider() {
        return provider;
    }

    public Farm setProvider(Provider provider) {
        this.provider = provider;
        return this;
    }

    public boolean isAutoReload() {
        return autoReload;
    }

    public Farm setAutoReload(boolean autoReload) {
        this.autoReload = autoReload;
        return this;
    }
}
