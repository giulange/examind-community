/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2018 Geomatys.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.constellation.business;

import java.util.List;
import java.util.Optional;
import org.constellation.dto.CstlUser;
import org.constellation.dto.UserWithRole;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
public interface IUserBusiness {

    List<CstlUser> findAll();

    Integer create(UserWithRole user);

    void update(UserWithRole user);

    int delete(int userId);

    int desactivate(int userId);

    int activate(int userId);

    boolean isLastAdmin(int userId);

    Optional<CstlUser> findOne(String login);

    Optional<CstlUser> findById(Integer id);

    Optional<CstlUser> findByEmail(String email);

    Optional<UserWithRole> findByForgotPasswordUuid(String uuid);

    List<String> getRoles(int userId);

    int countUser();

    boolean loginAvailable(String login);

    Optional<UserWithRole> findOneWithRole(Integer id);

    Optional<UserWithRole> findOneWithRole(String name);

    Optional<UserWithRole> findOneWithRoleByMail(String mail);

    List<UserWithRole> findActivesWithRole();

    List<UserWithRole> search(String search, int size, int page, String sortFieldName, String order);

    long searchCount(String search);

}
