/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2019 Geomatys.
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
package org.constellation.provider.observationstore;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.apache.sis.parameter.ParameterBuilder;
import org.apache.sis.storage.DataStoreProvider;
import org.constellation.provider.AbstractDataProviderFactory;
import org.constellation.provider.DataProvider;
import static org.constellation.provider.ProviderParameters.createDescriptor;
import org.constellation.provider.DataProviders;
import org.constellation.provider.ProviderParameters;
import org.opengis.parameter.GeneralParameterDescriptor;
import org.opengis.parameter.ParameterDescriptorGroup;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.util.GenericName;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
public class ObservationStoreProviderService extends AbstractDataProviderFactory {

  /**
     * Service name
     */
    public static final String NAME = "observation-store";
    public static final ParameterDescriptorGroup SOURCE_CONFIG_DESCRIPTOR  = ProviderParameters.buildSourceConfigDescriptor();
    public static final ParameterDescriptorGroup SERVICE_CONFIG_DESCRIPTOR = createDescriptor(SOURCE_CONFIG_DESCRIPTOR);

    public ObservationStoreProviderService(){
        super(NAME);
    }

    @Override
    public ParameterDescriptorGroup getProviderDescriptor() {
        return SERVICE_CONFIG_DESCRIPTOR;
    }

    @Override
    public ParameterDescriptorGroup getStoreDescriptor() {
        return SOURCE_CONFIG_DESCRIPTOR;
    }

    @Override
    public DataProvider createProvider(String providerId, ParameterValueGroup ps) {
        if (!canProcess(ps)) {
            return null;
        }

        try {
            final ObservationStoreProvider provider = new ObservationStoreProvider(providerId,this,ps);
            getLogger().log(Level.INFO, "[PROVIDER]> observation-store {0} provider created.", providerId);
            return provider;
        } catch (Exception ex) {
            // we should not catch exception, but here it's better to start all source we can
            // rather than letting a potential exception block the provider proxy
            getLogger().log(Level.SEVERE, "[PROVIDER]> Invalid observation-store provider config", ex);
        }
        return null;
    }

}
