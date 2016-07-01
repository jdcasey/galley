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

import org.apache.commons.lang.StringUtils;
import org.commonjava.maven.galley.maven.GalleyMavenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.List;
import java.util.Properties;

public class PropertiesView
    extends MavenPomElementView
{
    private Properties localProperties;

    private Properties properties;

    public PropertiesView( final MavenPomView pomView, final Element element, OriginInfo originInfo )
    {
        super( pomView, element, originInfo, "properties" );
    }

    /**
     * Return any key/value properties from within this view. Does NOT include inherited properties. If the local
     * properties section is missing, return empty Properties.
     *
     * @return Properties - containing key/values from this view.
     */
    public synchronized Properties getLocalProperties()
            throws GalleyMavenException
    {
        if ( localProperties == null )
        {
            localProperties = new Properties();
            extractPropertiesInto( getElement(), localProperties );
        }

        return localProperties;
    }

    public synchronized Properties getProperties()
            throws GalleyMavenException
    {
        Logger logger = LoggerFactory.getLogger( getClass() );
        if ( properties == null )
        {
            properties = new Properties();
            logger.debug( "Grabbing local properties..." );
            Properties localProperties = getLocalProperties();
            for ( String key : localProperties.stringPropertyNames() )
            {
                logger.debug( "Adding local: {}", key );
                properties.setProperty( key, localProperties.getProperty( key ) );
            }

            logger.debug( "Looking for aggregation source elements (inheritance)..." );
            List<MavenPomElementView> aggregationElements = getAggregationSourceElements( "/project/properties", true, false );
            for ( MavenPomElementView view : aggregationElements )
            {
                extractPropertiesInto( view.getElement(), properties );
            }
        }

        return properties;
    }

    private void extractPropertiesInto( Element element, Properties properties )
            throws GalleyMavenException
    {
        Logger logger = LoggerFactory.getLogger( getClass() );
        logger.debug( "Extracting properties from element: {}", element );
        if ( element != null )
        {
            NodeList children = element.getChildNodes();
            if ( children != null )
            {
                logger.debug( "Traversing {} child nodes.", children.getLength() );
                for ( int i = 0; i < children.getLength(); i++ )
                {
                    Node node = children.item( i );
                    if ( node instanceof Element )
                    {
                        String key = node.getNodeName();
                        logger.debug( "found property: {}", key );
                        if ( !properties.containsKey( key ) || StringUtils.isEmpty( properties.getProperty( key ) ) )
                        {
                            String value = getPomView().resolveExpressions( node.getTextContent().trim() );
                            logger.debug( "+= '{}': '{}'", key, value );
                            properties.setProperty( key, value );
                        }
                        else
                        {
                            logger.debug( "Skipping already-present key: '{}'", key );
                        }
                    }
                }
            }
        }
    }
}
