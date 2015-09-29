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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.security.access.prepost.PreAuthorize;

import io.galeb.manager.entity.Target;

import java.util.List;

@PreAuthorize("isFullyAuthenticated()")
@RepositoryRestResource(collectionResourceRel = "target", path = "target")
public interface TargetRepository extends JpaRepository<Target, Long>,
                                          FarmIDable<Target> {

    String QUERY_PREFIX = "SELECT ta FROM Target ta "
                            + "LEFT JOIN ta.project.teams t "
                            + "LEFT JOIN t.accounts a "
                            + "WHERE ";

    String IS_GLOBAL_FILTER = "ta.global = TRUE";

    String QUERY_FINDONE = QUERY_PREFIX + "ta.id = :id AND "
                            + "(" + CommonJpaFilters.SECURITY_FILTER + " OR "
                            + IS_GLOBAL_FILTER + ")";

    String QUERY_FINDBYNAME = QUERY_PREFIX + "ta.name = :name AND "
                            + "(" + CommonJpaFilters.SECURITY_FILTER + " OR "
                            + IS_GLOBAL_FILTER + ")";

    String QUERY_FINDALL = QUERY_PREFIX + CommonJpaFilters.SECURITY_FILTER + " OR "
                            + IS_GLOBAL_FILTER;

    String QUERY_FINDBYNAMECONTAINING = QUERY_PREFIX + "ta.name LIKE CONCAT('%',:name,'%') AND "
                            + "(" + CommonJpaFilters.SECURITY_FILTER + " OR "
                            + IS_GLOBAL_FILTER + ")";

    @Query(QUERY_FINDONE)
    Target findOne(@Param("id") Long id);

    @Query(QUERY_FINDBYNAME)
    Page<Target> findByName(@Param("name") String name, Pageable pageable);

    @Query(QUERY_FINDALL)
    Page<Target> findAll(Pageable pageable);

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    Page<Target> findByFarmId(@Param("id") long id, Pageable pageable);

    Page<Target> findByParentName(@Param("name") String name, Pageable pageable);

    @Query(QUERY_FINDBYNAMECONTAINING)
    Page<Target> findByNameContaining(@Param("name") String name, Pageable pageable);

}
