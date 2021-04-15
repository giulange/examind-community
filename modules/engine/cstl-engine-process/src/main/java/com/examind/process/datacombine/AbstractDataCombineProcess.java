/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2021 Geomatys.
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

import static com.examind.process.datacombine.AbstractDataCombineDescriptor.DATA;
import static com.examind.process.datacombine.AbstractDataCombineDescriptor.DATASET;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.sis.parameter.Parameters;
import org.constellation.business.IDataBusiness;
import org.constellation.business.IProviderBusiness;
import org.constellation.dto.process.DataProcessReference;
import org.constellation.dto.process.DatasetProcessReference;
import org.constellation.process.AbstractCstlProcess;
import org.constellation.repository.DataRepository;
import org.geotoolkit.process.ProcessDescriptor;
import org.geotoolkit.process.ProcessException;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterValue;
import org.opengis.parameter.ParameterValueGroup;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
public abstract class AbstractDataCombineProcess extends AbstractCstlProcess {

    @Autowired
    private DataRepository dataRepository;

    @Autowired
    protected IDataBusiness dataBusiness;

    @Autowired
    protected IProviderBusiness providerBusiness;

    public AbstractDataCombineProcess(final ProcessDescriptor desc, final ParameterValueGroup parameter) {
        super(desc, parameter);
    }

    protected List<Integer> getDataIdsToCombine() throws ProcessException {
        DatasetProcessReference dataset  = inputParameters.getValue(DATASET);
        List<DataProcessReference> datas = getValues(inputParameters, DATA.getName().getCode());

        if (dataset != null && !datas.isEmpty()) {
            throw new ProcessException("Either a list of data, a dataset should be given, not both.", this);
        } else if (dataset == null && datas.isEmpty()) {
            throw new ProcessException("No target data given for tiling operation. Either a list of data or dataset parameter must be set.", this);
        }

        List<Integer> dataIds;
        if (dataset != null) {
            dataIds = dataRepository.findByDatasetId(dataset.getId()).stream().map(f -> f.getId()).collect(Collectors.toList());
        } else {
            dataIds = new ArrayList<>();
            for (DataProcessReference dp : datas) {
                dataIds.add(dp.getId());
            }
        }

        return dataIds;
    }

    private List getValues(final Parameters param, final String descCode) {
        List results = new ArrayList<>();
        for (GeneralParameterValue value : param.values()) {
            if (value.getDescriptor().getName().getCode().equals(descCode)) {
                results.add(((ParameterValue) value).getValue());
            }
        }
        return results;
    }
}
