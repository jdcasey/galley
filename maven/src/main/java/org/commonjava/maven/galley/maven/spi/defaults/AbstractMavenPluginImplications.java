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
package org.commonjava.maven.galley.maven.spi.defaults;

import static org.commonjava.maven.galley.maven.model.view.XPathManager.A;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.G;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.V;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.galley.maven.GalleyMavenException;
import org.commonjava.maven.galley.maven.model.view.OriginInfo;
import org.commonjava.maven.galley.maven.model.view.PluginDependencyView;
import org.commonjava.maven.galley.maven.model.view.PluginView;
import org.commonjava.maven.galley.maven.parse.XMLInfrastructure;
import org.w3c.dom.Element;

public abstract class AbstractMavenPluginImplications
    implements MavenPluginImplications
{

    protected final XMLInfrastructure xml;

    protected AbstractMavenPluginImplications( final XMLInfrastructure xml )
    {
        this.xml = xml;
    }

    protected PluginDependencyView createPluginDependency( final PluginView pv, final ProjectRef ref )
        throws GalleyMavenException
    {
        final Map<String, String> map = new LinkedHashMap<String, String>();
        map.put( G, ref.getGroupId() );
        map.put( A, ref.getArtifactId() );
        map.put( V, pv.getVersion() );

        final Element element = pv.getElement();
        return new PluginDependencyView( pv.getPomView(), pv,
                                         xml.createElement( element, "dependencies/dependency", map ),
                                         new OriginInfo(), pv.getManagedPluginXpathFragment() );
    }

    @Override
    public Set<PluginDependencyView> getImpliedPluginDependencies( final PluginView pv )
        throws GalleyMavenException
    {
        final Map<ProjectRef, Set<ProjectRef>> impliedDepMap = getImpliedRefMap();
        final ProjectRef ref = pv.asProjectRef();
        final Set<ProjectRef> implied = impliedDepMap.get( ref );

        if ( implied == null || implied.isEmpty() )
        {
            return null;
        }

        final Set<PluginDependencyView> views = new HashSet<PluginDependencyView>( implied.size() );
        for ( final ProjectRef impliedRef : implied )
        {
            final PluginDependencyView pd = createPluginDependency( pv, impliedRef );
            views.add( pd );
        }

        return views;
    }

    protected abstract Map<ProjectRef, Set<ProjectRef>> getImpliedRefMap();

}
