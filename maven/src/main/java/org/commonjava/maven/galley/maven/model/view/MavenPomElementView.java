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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.galley.maven.GalleyMavenException;
import org.commonjava.maven.galley.maven.parse.ResolveFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class MavenPomElementView
        extends AbstractMavenElementView<MavenPomView>
{

    //    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final String managementXpathFragment;

    private OriginInfo originInfo;

    private String[] managementXpaths;

    private MavenPomElementView managementElement;

    public MavenPomElementView( final MavenPomView pomView, final Element element, OriginInfo originInfo )
    {
        super( pomView, element );
        this.originInfo = originInfo;
        this.managementXpathFragment = null;
    }

    public MavenPomElementView( final MavenPomView pomView, final Element element, OriginInfo originInfo,
                                final String managementXpathFragment )
    {
        super( pomView, element );
        this.originInfo = originInfo;
        this.managementXpathFragment = managementXpathFragment;
    }

    public synchronized OriginInfo getOriginInfo()
    {
        if ( originInfo == null )
        {
            originInfo = new OriginInfo();
        }

        return originInfo;
    }

    /**
     * Override this to provide the xpath fragment used to find management values for this view.
     */
    protected String getManagedViewQualifierFragment()
    {
        return null;
    }

    protected boolean containsExpression( final String value )
    {
        return xmlView.containsExpression( value );
    }

    public MavenPomView getPomView()
    {
        return xmlView;
    }

    public String getProfileId()
    {
        return xmlView.getProfileIdFor( element );
    }

    protected String getManagedValue( String named )
    {
        final MavenPomElementView mgmt = getManagementElement();
        if ( mgmt != null )
        {
            return mgmt.getValue( named );
        }

        return null;
    }

    protected String getValueWithManagement( final String named )
    {
        final String value = getValue( named );
        //        logger.info( "Value of path: '{}' local to: {} is: '{}'\nIn: {}", named, element, value, pomView.getRef() );
        if ( value == null )
        {
            return getManagedValue( named );
        }

        return value;
    }

    protected Element getManagedElement( String path )
    {
        final MavenPomElementView mgmt = getManagementElement();
        if ( mgmt != null )
        {
            return mgmt.getElement( path );
        }

        return null;
    }

    private synchronized MavenPomElementView getManagementElement()
    {
        if ( managementElement == null )
        {
            initManagementXpaths();
            if ( managementXpaths != null )
            {
                for ( final String xpath : managementXpaths )
                {
                    final MavenPomElementView e = xmlView.resolveXPathToElementView( xpath, false, -1 );
                    if ( e != null )
                    {
                        managementElement = e;
                        break;
                    }
                }
            }
        }

        return managementElement;
    }

    protected synchronized List<MavenPomElementView> getAggregationSourceElements( String path, boolean inherited,
                                                                                   boolean mixIns )
            throws GalleyMavenException
    {
        List<MavenPomElementView> result = new ArrayList<>();

        initManagementXpaths();
        if ( inherited )
        {
            List<DocRef<ProjectVersionRef>> stack = getPomView().getDocRefStack();
            int depth = 0;
            final MavenPomView oldView = ResolveFunctions.getPomView();
            try
            {
                ResolveFunctions.setPomView( getPomView() );
                for ( final DocRef<ProjectVersionRef> dr : stack )
                {
                    if ( depth != 0 )
                    {
                        Element n = (Element) dr.getDocContext()
                                                .selectSingleNode( path );

                        if ( n != null )
                        {
                            result.add( new MavenPomElementView( getPomView(), n, new OriginInfo( depth != 0 ) ) );
                        }

                        if ( managementXpaths != null )
                        {
                            for ( final String xpath : managementXpaths )
                            {
                                n = (Element) dr.getDocContext()
                                                .selectSingleNode( xpath );

                                if ( n != null )
                                {
                                    result.add( new MavenPomElementView( getPomView(), n, new OriginInfo( depth != 0 ) ) );
                                }

                            }
                        }
                    }

                    depth++;
                }
            }
            finally
            {
                ResolveFunctions.setPomView( oldView );
            }
        }

        if ( mixIns )
        {
            List<MavenXmlMixin<ProjectVersionRef>> mixins = getPomView().getMixins();

            for ( final MavenXmlMixin<ProjectVersionRef> mixin : mixins )
            {
                final MavenPomView mixinView = (MavenPomView) mixin.getMixin();
                if ( mixin.matches( path ) )
                {
                    MavenPomElementView e = mixinView.resolveXPathToElementView( path, false, -1 );
                    if ( e != null )
                    {
                        result.add( e );
                    }
                }

                for ( final String xpath : managementXpaths )
                {
                    if ( mixin.matches( xpath ) )
                    {
                        final MavenPomElementView e = mixinView.resolveXPathToElementView( xpath, false, -1 );
                        if ( e != null )
                        {
                            result.add( e );
                        }
                    }
                }
            }
        }

        return result;
    }

    protected List<XmlNodeInfo> getFirstNodesWithManagement( final String path )
            throws GalleyMavenException
    {
        //        logger.info( "Resolving '{}' from node: {}", path, this.element );
        final List<Node> nodes = xmlView.resolveXPathToNodeListFrom( elementContext, path, true );
        List<XmlNodeInfo> nodeInfos = new ArrayList<XmlNodeInfo>( nodes.size() );
        if ( nodes == null || nodes.isEmpty() )
        {
            final MavenPomElementView managedElement = getManagementElement();
            if ( managedElement != null )
            {
                nodeInfos = managedElement.getFirstNodesWithManagement( path );
                for ( XmlNodeInfo info : nodeInfos )
                {
                    info.setMixin( managedElement.isMixin() );
                }
            }
        }
        else
        {
            for ( Node node : nodes )
            {
                nodeInfos.add( new XmlNodeInfo( isInherited(), isMixin(), node ) );
            }
        }

        return nodeInfos;
    }

    private boolean isInherited()
    {
        return originInfo == null ? false : originInfo.isInherited();
    }

    private boolean isMixin()
    {
        return originInfo == null ? false : originInfo.isMixin();
    }

    protected String getManagementXpathFragment()
    {
        return managementXpathFragment;
    }

    private void initManagementXpaths()
    {
        if ( managementXpathFragment == null )
        {
            Logger logger = LoggerFactory.getLogger( getClass() );
            logger.debug( "No managementXpathFragment for: {}", getClass().getSimpleName() );
            managementXpaths = new String[0];
            return;
        }

        final String qualifier = getManagedViewQualifierFragment();

        final List<String> xpaths = new ArrayList<String>();

        final Set<String> activeProfiles = new HashSet<String>( xmlView.getActiveProfileIds() );
        activeProfiles.add( getProfileId() );

        for ( final String profileId : activeProfiles )
        {
            if ( profileId != null )
            {
                final StringBuilder sb = new StringBuilder();

                sb.append( "/project/profiles/profile[id/text()=\"" )
                  .append( profileId )
                  .append( "\"]/" )
                  .append( managementXpathFragment );

                if ( qualifier != null )
                {
                    sb.append( '[' ).append( qualifier ).append( "]" );
                }

                final String xp = sb.toString();
                xpaths.add( xp );
                //            logger.info( "Created management XPath template: '{}'", xp );
            }
        }

        final StringBuilder sb = new StringBuilder();
        sb.append( "/project/" ).append( managementXpathFragment ).append( '[' ).append( qualifier ).append( "]" );

        final String xp = sb.toString();
        xpaths.add( xp );
        //        logger.info( "Created management XPath template: '{}'", xp );

        managementXpaths = xpaths.toArray( new String[xpaths.size()] );
    }

    @Override
    protected String getValue( final String path )
    {
        final String val = super.getValue( path );
        if ( getProfileId() == null )
        {
            return xmlView.resolveExpressions( val );
        }
        else
        {
            return xmlView.resolveExpressions( val, getProfileId() );
        }
    }

}
