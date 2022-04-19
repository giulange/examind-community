/*
 *    Examind community - An open source and standard compliant SDI
 *    https://community.examind.com
 *
 * Copyright 2022 Geomatys.
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
package org.constellation.repository;

import java.util.List;
import org.constellation.dto.Theater;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
public interface TheaterRepository {

    List<Theater> findAll();

    Theater findById(Integer id);

    Theater findByName(String name);

    int delete(Integer id);

    int create(Theater theater);

    void addScene(Integer id, Integer sceneId);

    void removeAllScene(Integer id);

    void removeScene(Integer id, Integer sceneId);

    List<Theater> findForScene(Integer sceneId);
}
