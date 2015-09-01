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
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(uniqueConstraints = { @UniqueConstraint(name = "UK_name_targettype", columnNames = { "name" }) })
public class TargetType extends AbstractEntity<TargetType> {

    private static final long serialVersionUID = 5596582746795373013L;

    @OneToMany(mappedBy = "targetType")
    private Set<Target> targets;

    public TargetType(String name) {
        setName(name);
    }

    protected TargetType() {
        //
    }

    @Override
    @JoinColumn(foreignKey=@ForeignKey(name="FK_targettype_properties"))
    public Map<String, String> getProperties() {
        return super.getProperties();
    }

    public Set<Target> getTargets() {
        return targets;
    }

    public TargetType setTargets(Set<Target> targets) {
        if (targets != null) {
            targets.clear();
            targets.addAll(targets);
        }
        return this;
    }
}
