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
package org.commonjava.maven.galley.maven.model.view.settings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.commonjava.maven.galley.maven.GalleyMavenException;
import org.commonjava.maven.galley.maven.model.view.DocRef;
import org.commonjava.maven.galley.maven.model.view.MavenXmlView;
import org.commonjava.maven.galley.maven.model.view.XPathManager;
import org.commonjava.maven.galley.maven.parse.XMLInfrastructure;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class MavenSettingsView
    extends MavenXmlView<File>
{

    public MavenSettingsView( final List<DocRef<File>> stack, final XPathManager xpath,
                              final XMLInfrastructure xml )
    {
        super( stack, xpath, xml );
    }

    public List<String> getActiveProfiles()
        throws GalleyMavenException
    {
        return resolveValues( "/settings/activeProfiles/activeProfile/text()" );
    }

    public List<MirrorView> getMirrors()
    {
        final List<Node> nodes = resolveXPathToAggregatedNodeList( "/settings/mirrors", true, -1, false );
        final List<MirrorView> result = new ArrayList<MirrorView>();
        for ( final Node node : nodes )
        {
            result.add( new MirrorView( this, (Element) node ) );
        }

        return result;
    }

    public ProxyView getActiveProxy()
    {
        final Node proxyNode = resolveXPathToNode( "/settings/proxies/proxy[active/text() == 'true']", true, -1 );
        if ( proxyNode == null )
        {
            return null;
        }

        return new ProxyView( this, (Element) proxyNode );
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
