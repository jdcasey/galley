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

import org.commonjava.maven.galley.maven.GalleyMavenException;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

import static org.commonjava.maven.galley.maven.model.view.XPathManager.END_PAREN;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.EQQUOTE;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.ID;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.QUOTE;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.RESOLVE;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.TEXT;

public class PluginExecutionView
        extends AbstractPluginBaseView<PluginExecutionView>
{

    private final PluginView plugin;

    private String id;

    private String phase;

    private List<String> goals;

    public PluginExecutionView( final MavenPomView pomView, final PluginView plugin, final Element element,
                                final OriginInfo originInfo, final String managedPluginXpathFragment )
    {
        super( pomView, element, originInfo, managedPluginXpathFragment + "/executions/execution" );
        this.plugin = plugin;
    }

    public PluginView getPlugin()
    {
        return plugin;
    }

    public synchronized String getId()
    {
        if ( id == null )
        {
            id = getValue( "id" );
        }

        return id;
    }

    public synchronized String getPhase()
    {
        if ( phase == null )
        {
            phase = getValue( "phase" );
        }

        return phase;
    }

    public synchronized List<String> getGoals()
            throws GalleyMavenException
    {
        if ( goals == null )
        {
            List<XmlNodeInfo> nodes = getFirstNodesWithManagement( "goals" );
            if ( nodes != null )
            {
                final List<String> goals = new ArrayList<>();
                for ( final XmlNodeInfo node : nodes )
                {
                    goals.add( node.getText() );
                }

                this.goals = goals;
            }
        }

        return goals;
    }

    @Override
    protected String getManagedViewQualifierFragment()
    {
        final StringBuilder sb = new StringBuilder();

        sb.append( RESOLVE )
          .append( ID )
          .append( TEXT )
          .append( END_PAREN )
          .append( EQQUOTE )
          .append( getId() )
          .append( QUOTE );

        return sb.toString();
    }
}
