/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2014 Geomatys.
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

package org.constellation.wfs.core;

import java.util.function.Function;
import org.apache.sis.internal.filter.FunctionNames;
import org.geotoolkit.util.NamesExt;
import org.geotoolkit.filter.visitor.DuplicatingFilterVisitor;
import org.opengis.feature.FeatureType;
import org.opengis.filter.ValueReference;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
public class UnprefixerFilterVisitor extends DuplicatingFilterVisitor {

    public UnprefixerFilterVisitor(final FeatureType ft) {
        final Function previous = getExpressionHandler(FunctionNames.ValueReference);
        setExpressionHandler(FunctionNames.ValueReference, (e) -> {
            final ValueReference expression = (ValueReference) e;
            String prefix = "";
            if (NamesExt.getNamespace(ft.getName()) != null) {
                prefix = "{" + NamesExt.getNamespace(ft.getName()) + "}";
            }
            prefix = prefix + ft.getName().tip().toString();
            if (expression.getXPath().startsWith(prefix)) {
                final String newPropertyName = expression.getXPath().substring(prefix.length());
                return ff.property(newPropertyName);
            }
            return previous.apply(expression);
        });
    }
}
