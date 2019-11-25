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
package com.examind.sensor.ws;

import java.util.Collections;
import java.util.List;
import org.apache.sis.internal.storage.query.SimpleQuery;
import org.apache.sis.internal.system.DefaultFactories;
import org.constellation.api.ServiceDef;
import org.constellation.business.ISensorBusiness;
import org.constellation.dto.service.config.sos.SOSConfiguration;
import org.constellation.exception.ConfigurationException;
import org.constellation.exception.ConstellationStoreException;
import org.constellation.provider.DataProvider;
import org.constellation.provider.DataProviders;
import org.constellation.provider.ObservationProvider;
import org.constellation.provider.SensorProvider;
import org.constellation.ws.AbstractWorker;
import org.geotoolkit.filter.identity.DefaultFeatureId;
import org.geotoolkit.observation.ObservationStore;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.Id;
import org.opengis.observation.sampling.SamplingFeature;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
public abstract class SensorWorker extends AbstractWorker {

    /**
     * The sensor business
     */
    @Autowired
    protected ISensorBusiness sensorBusiness;

    protected SOSConfiguration configuration;

    /**
     * The sensorML provider identifier (to be removed)
     */
    protected Integer smlProviderID;

    /**
     * The Observation provider
     * TODO; find a way to remove the omStore calls
     */
    @Deprecated
    protected ObservationStore omStore;
    protected ObservationProvider omProvider;

    /**
     * The profile of the SOS service (transational/discovery).
     */
    protected boolean isTransactionnal;

    protected final FilterFactory ff;

    public SensorWorker(final String id, final ServiceDef.Specification specification) {
        super(id, specification);
        this.ff = DefaultFactories.forBuildin(FilterFactory.class);
        try {
            final Object object = serviceBusiness.getConfiguration(specification.name().toLowerCase(), id);
            if (object instanceof SOSConfiguration) {
                configuration = (SOSConfiguration) object;
            } else {
                startError("The configuration object is malformed or null.", null);
                return;
            }

            final List<Integer> providers = serviceBusiness.getLinkedProviders(getServiceId());

            // we initialize the reader/writer
            for (Integer providerID: providers) {
                DataProvider p = DataProviders.getProvider(providerID);
                if (p != null) {
                    // TODO for now we only take one provider by type
                    if (p instanceof SensorProvider) {
                        smlProviderID  = providerID;
                    }
                    // store may implements the 2 interface
                    if (p instanceof ObservationProvider) {
                        omProvider  = (ObservationProvider) p;
                        omStore     = (ObservationStore)p.getMainStore();
                    }
                } else {
                    startError("Unable to instanciate the provider:" + providerID, null);
                }
            }
        } catch (ConfigurationException ex) {
            startError(ex.getMessage(), ex);
        }
    }

    @Override
    protected final String getProperty(final String propertyName) {
        if (configuration != null) {
            return configuration.getParameter(propertyName);
        }
        return null;
    }

    protected boolean getBooleanProperty(final String propertyName, boolean defaultValue) {
        if (configuration != null) {
            return configuration.getBooleanParameter(propertyName, defaultValue);
        }
        return defaultValue;
    }

    protected SamplingFeature getFeatureOfInterest(String featureName, String version) throws ConstellationStoreException {
        final SimpleQuery subquery = new SimpleQuery();
        final Id filter = ff.id(Collections.singleton(new DefaultFeatureId(featureName)));
        subquery.setFilter(filter);
        List<SamplingFeature> sps = omProvider.getFeatureOfInterest(subquery, version);
        if (sps.isEmpty()) {
            return null;
        } else {
            return sps.get(0);
        }
    }
}
