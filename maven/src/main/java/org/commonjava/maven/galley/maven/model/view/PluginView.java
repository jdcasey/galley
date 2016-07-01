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

import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.galley.maven.GalleyMavenException;
import org.commonjava.maven.galley.maven.spi.defaults.MavenPluginDefaults;
import org.commonjava.maven.galley.maven.spi.defaults.MavenPluginImplications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.commonjava.maven.galley.maven.model.view.XPathManager.A;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.AND;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.END_PAREN;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.EQQUOTE;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.G;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.QUOTE;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.RESOLVE;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.TEXT;

/**
 * View class representing a plugin declaration. This declaration can be in a pluginManagement section, or in the main
 * plugins section, and it could be within a profile's build section. It extends {@link AbstractPluginBaseView} in order
 * to provide common views shared with {@link PluginExecutionView} the opportunity to store the parent view in a
 * constrained type (not {@link Object}). GAV access is provided by delegating to {@link MavenGAVHelper}.
 */
public class PluginView
        extends AbstractPluginBaseView<PluginView>
        implements ProjectVersionRefView
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private MavenGAVHelper helper;

    private final MavenPluginDefaults pluginDefaults;

    private List<PluginDependencyView> pluginDependencies;

    private final MavenPluginImplications pluginImplications;

    private String managedPluginXpathFragment;

    private List<PluginExecutionView> executions;

    protected PluginView( final MavenPomView pomView, final Element element, final OriginInfo originInfo,
                          final MavenPluginDefaults pluginDefaults, final MavenPluginImplications pluginImplications )
    {
        super( pomView, element, originInfo, "build/pluginManagement/plugins/plugin" );
        this.helper = new MavenGAVHelper( this );
        this.pluginDefaults = pluginDefaults;
        this.pluginImplications = pluginImplications;
    }

    public synchronized List<PluginDependencyView> getLocalPluginDependencies()
            throws GalleyMavenException
    {
        if ( pluginDependencies == null )
        {
            final List<PluginDependencyView> result = new ArrayList<PluginDependencyView>();

            final List<XmlNodeInfo> nodes = getFirstNodesWithManagement( "dependencies/dependency" );
            if ( nodes != null )
            {
                for ( final XmlNodeInfo node : nodes )
                {
                    logger.debug( "Adding plugin dependency for: {}", node.getNode().getNodeName() );
                    result.add( new PluginDependencyView( getPomView(), this, (Element) node.getNode(), node.getOriginInfo(),
                                                          getManagedPluginXpathFragment() ) );
                }

                this.pluginDependencies = result;
            }
        }

        return pluginDependencies;
    }

    /**
     * Retrieve the execution sections defined for this plugin. This will NOT return the implied executions sections
     * used by plugins bound to the lifecycle by default, or executed directly from the Maven command line.
     */
    public synchronized List<PluginExecutionView> getExecutions()
            throws GalleyMavenException
    {
        if ( executions == null )
        {
            final List<PluginExecutionView> result = new ArrayList<>();

            // Retrieve the execution sections relative to the current <plugin/> element.
            // FIXME: This should pull ALL exeuctions from ancestry AND management, and de-duplicate based on id.
//            List<MavenPomElementView> ancestryElements =
//                    getAggregationSourceElements( "executions/execution", true, false );
            final List<XmlNodeInfo> nodes = getFirstNodesWithManagement( "executions/execution" );
            if ( nodes != null )
            {
                for ( final XmlNodeInfo node : nodes )
                {
                    logger.debug( "Adding plugin dependency for: {}", node.getNode().getNodeName() );
                    result.add( new PluginExecutionView( getPomView(), this, (Element) node.getNode(), node.getOriginInfo(),
                                                          getManagedPluginXpathFragment() ) );
                }

                this.executions = result;
            }
        }

        return executions;
    }

    public synchronized String getManagedPluginXpathFragment()
    {
        if ( managedPluginXpathFragment == null )
        {
            StringBuilder sb = new StringBuilder();
            sb.append( getManagementXpathFragment() )
              .append( '[' )
              .append( getManagedViewQualifierFragment() )
              .append( "]" );

            managedPluginXpathFragment = sb.toString();
        }

        return managedPluginXpathFragment;
    }

    public Set<PluginDependencyView> getImpliedPluginDependencies()
            throws GalleyMavenException
    {
        return pluginImplications.getImpliedPluginDependencies( this );
    }

    @Override
    public synchronized String getVersion()
            throws GalleyMavenException
    {
        String version = helper.getVersion();
        if ( version == null )
        {
            version = pluginDefaults.getDefaultVersion( getGroupId(), getArtifactId() );
            helper.setVersion( version );
        }

        return version;
    }

    @Override
    public ProjectVersionRef asProjectVersionRef()
            throws GalleyMavenException
    {
        return helper.asProjectVersionRef();
    }

    @Override
    public synchronized String getGroupId()
    {
        String gid = helper.getGroupId();
        if ( gid == null )
        {
            gid = pluginDefaults.getDefaultGroupId( getArtifactId() );
            helper.setGroupId( gid );
        }

        return gid;
    }

    @Override
    public String getArtifactId()
    {
        return helper.getArtifactId();
    }

    @Override
    public ProjectRef asProjectRef()
            throws GalleyMavenException
    {
        return helper.asProjectRef();
    }

    @Override
    protected String getManagedViewQualifierFragment()
    {
        final StringBuilder sb = new StringBuilder();

        final String aid = getArtifactId();
        final String gid = getGroupId();
        final String dgid = pluginDefaults.getDefaultGroupId( aid );
        if ( !gid.equals( dgid ) )
        {
            sb.append( RESOLVE )
              .append( G )
              .append( TEXT )
              .append( END_PAREN )
              .append( EQQUOTE )
              .append( gid )
              .append( QUOTE )
              .append( AND );
        }

        sb.append( RESOLVE )
          .append( A )
          .append( TEXT )
          .append( END_PAREN )
          .append( EQQUOTE )
          .append( aid )
          .append( QUOTE );

        return sb.toString();
    }
}
