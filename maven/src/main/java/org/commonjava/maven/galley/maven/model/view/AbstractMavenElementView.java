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
import java.util.List;

import org.apache.commons.jxpath.JXPathContext;
import org.commonjava.maven.galley.maven.parse.JXPathUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public abstract class AbstractMavenElementView<T extends MavenXmlView<?>>
{

    //    private final Logger logger = LoggerFactory.getLogger( getClass() );

    protected final Element element;

    protected final JXPathContext elementContext;

    protected final T xmlView;

    public AbstractMavenElementView( final T xmlView, final Element element )
    {
        this.xmlView = xmlView;
        this.element = element;

        this.elementContext = JXPathUtils.newContext( element );
    }

    public final Element getElement()
    {
        return element;
    }

    public final T getXmlView()
    {
        return xmlView;
    }

    protected String getValue( final String path )
    {
        final Node node = xmlView.resolveXPathToNodeFrom( elementContext, path, false );
        if ( node == null )
        {
            return null;
        }

        return node.getTextContent()
                .trim();
    }

    protected final Element getElement( final String path )
    {
        if ( path.contains( "/" ) )
        {
            return (Element) getNode( path );
        }
        else
        {
            final NodeList nl = element.getElementsByTagName( path );
            if ( nl.getLength() > 0 )
            {
                return (Element) nl.item( 0 );
            }
        }

        return null;
    }

    protected final List<Element> getElements( final String path )
    {
        if ( path.contains( "/" ) )
        {
            final List<Node> nodes = getNodes( path );
            if ( nodes != null )
            {
                final List<Element> elements = new ArrayList<Element>();
                for ( final Node node : nodes )
                {
                    elements.add( (Element) node );
                }

                return elements;
            }
        }
        else
        {
            final NodeList nl = element.getChildNodes();
            final List<Element> elements = new ArrayList<Element>();
            for ( int i = 0; i < nl.getLength(); i++ )
            {
                final Node node = nl.item( i );
                if ( node.getNodeType() == Node.ELEMENT_NODE )
                {
                    final Element e = (Element) node;
                    if ( "*".equals( path ) || e.getTagName()
                                                .equals( path ) )
                    {
                        elements.add( (Element) node );
                    }
                }
            }

            return elements;
        }

        return null;
    }

    protected final Node getNode( final String path )
    {
        return (Node) elementContext.selectSingleNode( path );
    }

    @SuppressWarnings( "unchecked" )
    protected final List<Node> getNodes( final String path )
    {
        return elementContext.selectNodes( path );
    }

    public final String toXML()
    {
        return xmlView.toXML( element );
    }

    protected final String toXML( final boolean printXmlDeclaration )
    {
        return xmlView.toXML( element, printXmlDeclaration );
    }

}
