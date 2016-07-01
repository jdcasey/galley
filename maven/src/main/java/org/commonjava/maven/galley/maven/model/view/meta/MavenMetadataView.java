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
package org.commonjava.maven.galley.maven.model.view.meta;

import java.util.List;

import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.galley.maven.GalleyMavenException;
import org.commonjava.maven.galley.maven.model.view.DocRef;
import org.commonjava.maven.galley.maven.model.view.MavenXmlView;
import org.commonjava.maven.galley.maven.model.view.XPathManager;
import org.commonjava.maven.galley.maven.parse.XMLInfrastructure;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class MavenMetadataView
    extends MavenXmlView<ProjectRef>
{

    public MavenMetadataView( final List<DocRef<ProjectRef>> stack, final XPathManager xpath,
                              final XMLInfrastructure xml )
    {
        super( stack, xpath, xml );
    }

    public VersioningView getVersioning()
    {
        final Node node = resolveXPathToNode( "/metadata/versioning", true, -1 );
        if ( node != null && node.getNodeType() == Node.ELEMENT_NODE )
        {
            return new VersioningView( this, (Element) node );
        }

        return null;
    }

    public String resolveSingleValue( final String path )
        throws GalleyMavenException
    {
        return resolveXPathToRawString( path, true, -1 );
    }

    public List<String> resolveValues( final String path )
        throws GalleyMavenException
    {
        return resolveXPathToAggregatedStringList( path, true, -1, false );
    }

}
