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
package com.examind.process.datacombine;

import com.examind.process.admin.AdminProcessDescriptor;
import com.examind.process.admin.AdminProcessRegistry;
import org.apache.sis.metadata.iso.DefaultIdentifier;
import org.apache.sis.metadata.iso.citation.DefaultCitation;
import org.apache.sis.parameter.ParameterBuilder;
import org.apache.sis.util.SimpleInternationalString;
import org.geotoolkit.processing.AbstractProcessDescriptor;
import org.opengis.parameter.ParameterDescriptor;
import org.opengis.parameter.ParameterDescriptorGroup;
import org.opengis.parameter.ParameterValueGroup;

import static com.examind.process.datacombine.AbstractDataCombineDescriptor.*;

/**
 * Based on {@link  AbstractDataCombineDescriptor}
 */
public class FeatureToHeatMapDescriptor extends AbstractProcessDescriptor implements AdminProcessDescriptor {

    public static final ParameterDescriptor<Integer> TILING_DIMENSION_X;
    public static final ParameterDescriptor<Integer> TILING_DIMENSION_Y;

    public static final ParameterDescriptor<Float> DISTANCE_X;
    public static final ParameterDescriptor<Float> DISTANCE_Y;

    public static final ParameterDescriptor<String> ALGORITHM;

    public static final ParameterDescriptorGroup INPUT;

    public static final ParameterDescriptorGroup OUTPUT;

    static {
        final ParameterBuilder builder = new ParameterBuilder();
        builder.setRequired(false);

        TILING_DIMENSION_X = builder.addName("tiling.x")
                .setDescription("Expected tile width. Default value : null for the use of a single tile on X axis.")
                .create(Integer.class, null);

        TILING_DIMENSION_Y = builder.addName("tiling.y")
                .setDescription("Expected tile height. Default value : null for the use of a single tile on Y axis.")
                .create(Integer.class, null);

        builder.setRequired(true);

        DISTANCE_X = builder.addName("distance.x")
                .setDescription("Distance along the first CRS dimension to take into account for the HeatMap computation")
                .create(Float.class, null);

        DISTANCE_Y = builder.addName("distance.y")
                .setDescription("Distance along the second CRS dimension to take into account for the HeatMap computation")
                .create(Float.class, null);

        ALGORITHM = builder.addName("algorithm")
                .setDescription("Algorithm to use to compute the represent the influence of each data points in the heat map.")
                .createEnumerated(String.class, new String[]{Algorithm.GAUSSIAN_MASK.name(), Algorithm.GAUSSIAN.name(), Algorithm.EUCLIDEAN.name(), Algorithm.ONE.name() }, Algorithm.GAUSSIAN_MASK.name());

        INPUT = builder.addName("input")
                .createGroup(DATA_NAME, TARGET_DATASET, DATA ,TILING_DIMENSION_X, TILING_DIMENSION_Y, DISTANCE_X, DISTANCE_Y, ALGORITHM);

        OUTPUT = builder.addName("output")
                .createGroup();
    }

    public FeatureToHeatMapDescriptor() {
        super(new DefaultIdentifier(new DefaultCitation(AdminProcessRegistry.NAME), "HeatMap coverage"), new SimpleInternationalString("Combine featureFet data in a heatmap"), INPUT, OUTPUT);
    }


    @Override
    public org.geotoolkit.process.Process createProcess(ParameterValueGroup input) {
        return new FeaturesToHeatMapProcess(this, input);
    }

    private enum Algorithm { EUCLIDEAN, GAUSSIAN, GAUSSIAN_MASK, ONE}

}