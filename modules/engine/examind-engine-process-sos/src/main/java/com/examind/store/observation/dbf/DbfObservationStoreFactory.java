/*
 *    Geotoolkit - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2014, Geomatys
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package com.examind.store.observation.dbf;

import com.examind.store.observation.FileParsingObservationStoreFactory;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.apache.sis.internal.storage.Capability;
import org.apache.sis.internal.storage.StoreMetadata;
import org.apache.sis.metadata.iso.citation.Citations;
import org.apache.sis.parameter.DefaultParameterDescriptorGroup;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.storage.FeatureSet;
import org.geotoolkit.data.dbf.DbaseFileProvider;
import org.geotoolkit.storage.ResourceType;
import org.geotoolkit.storage.StoreMetadataExt;
import org.opengis.metadata.Identifier;
import org.opengis.parameter.ParameterDescriptor;
import org.opengis.parameter.ParameterDescriptorGroup;
import org.opengis.parameter.ParameterValueGroup;

/**
 *
 * @author Samuel Andrés (Geomatys)
 * @author Guilhem Legal (Geomatys)
 */
@StoreMetadata(
        formatName = DbfObservationStoreFactory.NAME,
        capabilities = Capability.READ,
        resourceTypes = {FeatureSet.class})
@StoreMetadataExt(resourceTypes = ResourceType.SENSOR)
public class DbfObservationStoreFactory extends FileParsingObservationStoreFactory {

    /** factory identification **/
    public static final String NAME = "observationDbfFile";

    public static final ParameterDescriptor<String> IDENTIFIER = createFixedIdentifier(NAME);

    public static final ParameterDescriptorGroup PARAMETERS_DESCRIPTOR
            = PARAM_BUILDER.addName(NAME).addName("ObservationDbfFileParameters").createGroup(IDENTIFIER, NAMESPACE, DbaseFileProvider.PATH,
                    MAIN_COLUMN, DATE_COLUMN, DATE_FORMAT, LONGITUDE_COLUMN, LATITUDE_COLUMN, OBS_PROP_COLUMN, FOI_COLUMN, OBSERVATION_TYPE, PROCEDURE_ID, PROCEDURE_COLUMN);

    private static ParameterDescriptorGroup parameters(final String name, final int minimumOccurs) {
        final Map<String,Object> properties = new HashMap<>(4);
        properties.put(ParameterDescriptorGroup.NAME_KEY, name);
        properties.put(Identifier.AUTHORITY_KEY, Citations.SIS);
        return new DefaultParameterDescriptorGroup(properties, minimumOccurs, 1);
    }

    @Override
    public String getShortName() {
        return NAME;
    }

    @Override
    public ParameterDescriptorGroup getOpenParameters() {
        return PARAMETERS_DESCRIPTOR;
    }

    @Override
    public DbfObservationStore open(final ParameterValueGroup params) throws DataStoreException {
        final URI uri = (URI) params.parameter(DbaseFileProvider.PATH.getName().toString()).getValue();
        final String mainColumn = (String) params.parameter(MAIN_COLUMN.getName().toString()).getValue();
        final String dateColumn = (String) params.parameter(DATE_COLUMN.getName().toString()).getValue();
        final String dateFormat = (String) params.parameter(DATE_FORMAT.getName().toString()).getValue();
        final String longitudeColumn = (String) params.parameter(LONGITUDE_COLUMN.getName().toString()).getValue();
        final String latitudeColumn = (String) params.parameter(LATITUDE_COLUMN.getName().toString()).getValue();
        final String foiColumn = (String) params.parameter(FOI_COLUMN.getName().toString()).getValue();
        final String procedureId = (String) params.parameter(PROCEDURE_ID.getName().toString()).getValue();
        final String procedureColumn = (String) params.parameter(PROCEDURE_COLUMN.getName().toString()).getValue();
        final String observationType = (String) params.parameter(OBSERVATION_TYPE.getName().toString()).getValue();
        final Set<String> obsPropColumns = getMultipleValues(params, OBS_PROP_COLUMN.getName().toString());
        try {
            return new DbfObservationStore(Paths.get(uri),
                    mainColumn, dateColumn, dateFormat, longitudeColumn, latitudeColumn, obsPropColumns, observationType, foiColumn, procedureId, procedureColumn);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "problem opening dbf file", ex);
            throw new DataStoreException(ex);
        }
    }

    @Override
    public Collection<String> getSuffix() {
        return Arrays.asList("dbf");
    }
}
