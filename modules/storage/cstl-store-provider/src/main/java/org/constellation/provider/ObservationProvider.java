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
package org.constellation.provider;

import java.util.Collection;
import java.util.List;
import javax.xml.namespace.QName;
import org.apache.sis.storage.Query;
import org.constellation.dto.service.config.sos.ProcedureTree;
import org.constellation.dto.service.config.sos.SOSProviderCapabilities;
import org.constellation.exception.ConstellationStoreException;
import org.opengis.observation.Observation;
import org.opengis.observation.Phenomenon;
import org.opengis.observation.Process;
import org.opengis.observation.sampling.SamplingFeature;
import org.opengis.temporal.TemporalGeometricPrimitive;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
public interface ObservationProvider extends DataProvider {

    List<ProcedureTree> getProcedures() throws ConstellationStoreException;

    Collection<String> getPhenomenonNames() throws ConstellationStoreException;

    Collection<Phenomenon> getPhenomenon(Query query, String version) throws ConstellationStoreException;

    Collection<String> getProcedureNames(String typeFilter) throws ConstellationStoreException;

    Collection<String> getFeatureOfInterestNames() throws ConstellationStoreException;

    List<SamplingFeature> getFeatureOfInterest(Query query, String version) throws ConstellationStoreException;

    List<Observation> getObservations(Query query, QName resultModel, String responseMode, String version) throws ConstellationStoreException;

    List<Process> getProcedures(Query query, String version) throws ConstellationStoreException;

    SOSProviderCapabilities getCapabilities()  throws ConstellationStoreException;

    Object getSensorLocation(final String sensorID, final String gmlVersion) throws ConstellationStoreException;

    boolean existPhenomenon(final String phenomenonName) throws ConstellationStoreException;

    boolean existProcedure(final String procedureName) throws ConstellationStoreException;

    Collection<String> getProceduresForPhenomenon(final String observedProperty) throws ConstellationStoreException;

    Collection<String> getPhenomenonsForProcedure(final String sensorID) throws ConstellationStoreException;

    TemporalGeometricPrimitive getTimeForProcedure(final String version, final String sensorID) throws ConstellationStoreException;

    void removeProcedure(String procedureID) throws ConstellationStoreException;

    String writeObservation(final Observation observation) throws ConstellationStoreException;

    void removeObservation(final String observationID) throws ConstellationStoreException;

    void removeObservationForProcedure(final String procedureID) throws ConstellationStoreException;

    void writeProcedure(final String procedureID, final Object position, final String parent, final String type) throws ConstellationStoreException;

    void updateProcedureLocation(final String procedureID, final Object position) throws ConstellationStoreException;
}