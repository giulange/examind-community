/*
 *    Examind community - An open source and standard compliant SDI
 *    https://community.examind.com/
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
package org.constellation.converter;

import com.fasterxml.jackson.databind.util.StdConverter;
import org.apache.sis.storage.Resource;
import org.constellation.dto.process.DataProcessReference;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
public class DataProcessReferenceConverter extends StdConverter<DataProcessReference, Resource> {

    @Override
    public Resource convert(DataProcessReference in) {
        return new DataProcessReferenceToResourceConverter().apply(in);
    }
}