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

package io.galeb.manager.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.security.access.prepost.PreAuthorize;

import io.galeb.manager.entity.VirtualHost;

@PreAuthorize("isFullyAuthenticated()")
@RepositoryRestResource(collectionResourceRel = "virtualhost", path = "virtualhost")
public interface VirtualHostRepository extends PagingAndSortingRepository<VirtualHost, Long> {

    @Override
    @Query("SELECT v FROM VirtualHost v "
           + "INNER JOIN v.project.teams t "
           + "INNER JOIN t.accounts a "
           + "WHERE v.id = :id AND "
               + "(1 = ?#{hasRole('ROLE_ADMIN') ? 1 : 0} OR "
               + "a.name = ?#{principal.username})")
    VirtualHost findOne(@Param("id") Long id);

    @Query("SELECT v FROM VirtualHost v "
            + "INNER JOIN v.project.teams t "
            + "INNER JOIN t.accounts a "
            + "WHERE v.name = :name AND "
                + "(1 = ?#{hasRole('ROLE_ADMIN') ? 1 : 0} OR "
                + "a.name = ?#{principal.username})")
    List<VirtualHost> findByName(@Param("name") String name);

    @Override
    @Query("SELECT v FROM VirtualHost v "
            + "INNER JOIN v.project.teams t "
            + "INNER JOIN t.accounts a "
            + "WHERE 1 = ?#{hasRole('ROLE_ADMIN') ? 1 : 0} OR "
                + "a.name = ?#{principal.username}")
    List<VirtualHost> findAll();

}
