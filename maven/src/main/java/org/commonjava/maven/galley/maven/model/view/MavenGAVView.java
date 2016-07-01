/**
 * Copyright (C) 2013 Red Hat, Inc. (jdcasey@commonjava.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.maven.galley.maven.model.view;

import static org.commonjava.maven.galley.maven.model.view.XPathManager.V;

import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.galley.maven.GalleyMavenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

/**
 * Generic view that represents some reference to a Maven project version. It <b>CAN</b> also represent a reference to an
 * actual artifact, <b>IF</b> that artifact will never have a classifier, and will always use the type 'jar'.
 */
public class MavenGAVView
        extends MavenGAView
        implements ProjectVersionRefView
{

    public MavenGAVView( final MavenPomView pomView, final Element element, final OriginInfo originInfo, final String managementXpathFragment )
    {
        super( pomView, element, originInfo, managementXpathFragment );
    }

    public MavenGAVView( final MavenPomView pomView, final Element element, final OriginInfo originInfo )
    {
        this( pomView, element, originInfo, null );
    }

    @Override
    public String getVersion()
            throws GalleyMavenException
    {
        return helper.getVersion();
    }

    @Override
    public ProjectVersionRef asProjectVersionRef()
            throws GalleyMavenException
    {
        return helper.asProjectVersionRef();
    }

    public String toString()
    {
        return helper.gavToString();
    }

    @Override
    public boolean isValid()
    {
        return helper.gavIsValid();
    }
}
