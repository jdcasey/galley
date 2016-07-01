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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.JXPathException;
import org.apache.commons.jxpath.JXPathInvalidSyntaxException;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;
import org.commonjava.maven.galley.maven.GalleyMavenRuntimeException;
import org.commonjava.maven.galley.maven.parse.XMLInfrastructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Base class for objects parsed from XML documents that can have inheritance or mix-ins (eg. merged Maven 
 * metadata documents or Maven BOMs).
 * 
 * @author jdcasey
 *
 * @param <T> The type of project reference this document is associated with (eg. ProjectVersionRef for a POM 
 *   or versionless ProjectRef for project-level Maven metadata)
 */
public class MavenXmlView<T>
{

    protected final Logger logger = LoggerFactory.getLogger( getClass() );

    protected final List<DocRef<T>> stack;

    //    protected final XPathManager xpath;

    protected StringSearchInterpolator ssi;

    protected final List<MavenXmlMixin<T>> mixins = new ArrayList<MavenXmlMixin<T>>();

    protected final Set<String> localOnlyPaths;

    protected final XMLInfrastructure xml;

    public MavenXmlView( final List<DocRef<T>> stack, final XPathManager xpath, final XMLInfrastructure xml,
                         final String... localOnlyPaths )
    {
        this.stack = stack;
        //        this.xpath = xpath;
        this.xml = xml;
        this.localOnlyPaths = new HashSet<String>( Arrays.asList( localOnlyPaths ) );
    }

    /**
     * Retrieve the project reference that this view is associated with.
     */
    public T getRef()
    {
        return stack.get( 0 )
                    .getRef();
    }

    /**
     * Retrieve the list of raw documents that constitute the inheritance hierarchy for this view.
     */
    public List<DocRef<T>> getDocRefStack()
    {
        return stack;
    }

    /**
     * Retrieve the first node matching the given XPath expression, including the inheritance hierarchy documents
     * up to the specified maxDepth (if maxDepth < 0, consider the full inheritance hierarchy). Also include 
     * any mix-in documents in the search. If cachePath is true, compile the XPath instance and cache it for 
     * reuse in future queries. If the XPath expression resolves to something other than a text value, 
     * retrieve the text() child.
     * <br/>
     * Do NOT resolve Maven-style expressions on the resulting value.
     * <br/>
     * If the XPath expression is listed as local-only (specified when the view is constructed), do NOT search
     * beyond the current document.
     * 
     * @param path The XPath expression
     * @param cachePath If true, compile this XPath expression and cache for future use
     * @param maxDepth Max ancestry depth to search. If < 0, search all ancestors.
     */
    public String resolveXPathToRawString( final String path, final boolean cachePath, final int maxDepth )
        throws GalleyMavenRuntimeException
    {
        String result = null;
        //        try
        //        {
        //            final XPathExpression expression = xpath.getXPath( path, cachePath );

        int maxAncestry = maxDepth;
        for ( final String pathPrefix : localOnlyPaths )
        {
            if ( path.startsWith( pathPrefix ) )
            {
                maxAncestry = 0;
                break;
            }
        }

        //        logger.info( "Resolving: {}", path );

        int ancestryDepth = 0;
        for ( final DocRef<T> dr : stack )
        {
            if ( maxAncestry > -1 && ancestryDepth > maxAncestry )
            {
                break;
            }

            try
            {
                result = (String) dr.getDocContext()
                                    .getValue( path );
            }
            catch ( final JXPathInvalidSyntaxException e )
            {
                logger.debug( "[ABORT XPath] Error resolving '{}' from '{}': {}", e, path, dr.getSource(),
                              e.getMessage() );
                return null;
            }
            catch ( final JXPathException e )
            {
                logger.debug( "[SKIP XPath-Doc] Error resolving '{}' from '{}': {}", e, path, dr.getSource(),
                              e.getMessage() );
                continue;
            }

            //                result = (Node) expression.evaluate( dr.getDoc(), XPathConstants.NODE );
            //                logger.info( "Value of '{}' at depth: {} is: {}", path, ancestryDepth, result );

            if ( result != null )
            {
                break;
            }

            ancestryDepth++;
        }

        if ( result == null )
        {
            for ( final MavenXmlMixin<T> mixin : mixins )
            {
                if ( mixin.matches( path ) )
                {
                    result = mixin.getMixin()
                                  .resolveXPathToRawString( path, true, maxAncestry );
                    //                        logger.info( "Value of '{}' in mixin: {} is: '{}'", path, mixin );
                }

                if ( result != null )
                {
                    break;
                }
            }
        }
        //        }
        //        catch ( final XPathExpressionException e )
        //        {
        //            throw new GalleyMavenRuntimeException( "Failed to retrieve content for xpath expression: {}. Reason: {}", e, path, e.getMessage() );
        //        }

        return result;
    }

    /**
     * Retrieve the first node matching the given XPath expression, including the inheritance hierarchy documents
     * up to the specified maxDepth (if maxDepth < 0, consider the full inheritance hierarchy). Also include 
     * any mix-in documents in the search. If cachePath is true, compile the XPath instance and cache it for 
     * reuse in future queries.
     * <br/>
     * If the XPath expression is listed as local-only (specified when the view is constructed), do NOT search
     * beyond the current document.
     * 
     * @param path The XPath expression
     * @param cachePath If true, compile this XPath expression and cache for future use
     * @param maxDepth Max ancestry depth to search. If < 0, search all ancestors.
     */
    public Node resolveXPathToNode( final String path, final boolean cachePath, final int maxDepth )
        throws GalleyMavenRuntimeException
    {
        Node result = null;
        //        try
        //        {
        //            final XPathExpression expression = xpath.getXPath( path, cachePath );

        int maxAncestry = maxDepth;
        for ( final String pathPrefix : localOnlyPaths )
        {
            if ( path.startsWith( pathPrefix ) )
            {
                maxAncestry = 0;
                break;
            }
        }

        //        logger.info( "Resolving: {}", path );

        int ancestryDepth = 0;
        for ( final DocRef<T> dr : stack )
        {
            if ( maxAncestry > -1 && ancestryDepth > maxAncestry )
            {
                break;
            }

            result = (Node) dr.getDocContext()
                              .selectSingleNode( path );
            //                result = (Node) expression.evaluate( dr.getDoc(), XPathConstants.NODE );
            //                logger.info( "Value of '{}' at depth: {} is: {}", path, ancestryDepth, result );

            if ( result != null )
            {
                break;
            }

            ancestryDepth++;
        }

        if ( result == null )
        {
            for ( final MavenXmlMixin<T> mixin : mixins )
            {
                if ( mixin.matches( path ) )
                {
                    result = mixin.getMixin()
                                  .resolveXPathToNode( path, true, maxAncestry );
                    //                        logger.info( "Value of '{}' in mixin: {} is: '{}'", path, mixin );
                }

                if ( result != null )
                {
                    break;
                }
            }
        }
        //        }
        //        catch ( final XPathExpressionException e )
        //        {
        //            throw new GalleyMavenRuntimeException( "Failed to retrieve content for xpath expression: {}. Reason: {}", e, path, e.getMessage() );
        //        }

        return result;
    }

    /**
     * Retrieve the ordered list of nodes matching the given XPath expression, including the inheritance 
     * hierarchy documents up to the specified maxDepth (if maxDepth < 0, consider the full inheritance 
     * hierarchy). Also include any mix-in documents in the search. If cachePath is true, compile the XPath 
     * instance and cache it for reuse in future queries.
     * <br/>
     * If the XPath expression is listed as local-only (specified when the view is constructed), do NOT search
     * beyond the current document.
     * 
     * @param path The XPath expression
     * @param cachePath If true, compile this XPath expression and cache for future use
     * @param maxDepth Max ancestry depth to search. If < 0, search all ancestors.
     * @param includeMixins Whether to query mixin POMs for the same hits.
     */
    public synchronized List<Node> resolveXPathToAggregatedNodeList( final String path, final boolean cachePath,
                                                                     final int maxDepth, final boolean includeMixins )
        throws GalleyMavenRuntimeException
    {
        int maxAncestry = maxDepth;
        for ( final String pathPrefix : localOnlyPaths )
        {
            if ( path.startsWith( pathPrefix ) )
            {
                maxAncestry = 0;
                break;
            }
        }

        int ancestryDepth = 0;
        final List<Node> result = new ArrayList<Node>();
        for ( final DocRef<T> dr : stack )
        {
            if ( maxAncestry > -1 && ancestryDepth > maxAncestry )
            {
                break;
            }

            final List<Node> nodes = getLocalNodeList( dr.getDocContext(), path );
            if ( nodes != null )
            {
                for ( final Node node : nodes )
                {
                    result.add( node );
                }
            }

            ancestryDepth++;
        }

        if ( includeMixins )
        {
            for ( final MavenXmlMixin<T> mixin : mixins )
            {
                if ( !mixin.matches( path ) )
                {
                    continue;
                }

                final List<Node> nodes = mixin.getMixin()
                                              .resolveXPathToAggregatedNodeList( path, cachePath, maxAncestry, includeMixins );
                if ( nodes != null )
                {
                    for ( final Node node : nodes )
                    {
                        result.add( node );
                    }
                }
            }
        }

        return result;
    }

    /**
     * Traverse through the document stack in the following order, looking for the first document that contains
     * one or more nodes matching the given XPath expression:
     * <ol>
     *   <li>local document</li>
     *   <li>ancestry documents</li>
     *   <li>mix-ins</li>
     * </ol>
     * 
     * If the XPath expression is listed as local-only (specified when the view is constructed), do NOT search
     * beyond the current document.
     * 
     * @param path The XPath expression
     * @param cachePath If true, compile this XPath expression and cache for future use
     * @param maxDepth Max ancestry depth to search. If < 0, search all ancestors.
     */
    public synchronized List<Node> resolveXPathToFirstNodeList( final String path, final boolean cachePath,
                                                                final int maxDepth )
        throws GalleyMavenRuntimeException
    {
        int maxAncestry = maxDepth;
        for ( final String pathPrefix : localOnlyPaths )
        {
            if ( path.startsWith( pathPrefix ) )
            {
                maxAncestry = 0;
                break;
            }
        }

        int ancestryDepth = 0;
        for ( final DocRef<T> dr : stack )
        {
            if ( maxAncestry > -1 && ancestryDepth > maxAncestry )
            {
                break;
            }

            final List<Node> result = getLocalNodeList( dr.getDocContext(), path );
            if ( result != null )
            {
                return result;
            }

            ancestryDepth++;
        }

        for ( final MavenXmlMixin<T> mixin : mixins )
        {
            if ( !mixin.matches( path ) )
            {
                continue;
            }

            final List<Node> result = mixin.getMixin()
                                           .resolveXPathToFirstNodeList( path, cachePath, maxAncestry );
            if ( result != null )
            {
                return result;
            }
        }

        return null;
    }

    /**
     * Select the ordered list of nodes matching the given XPath expression, rooted in the given context.
     */
    protected List<Node> getLocalNodeList( final JXPathContext context, final String path )
        throws GalleyMavenRuntimeException
    {
        final List<Node> result = new ArrayList<Node>();
        final List<?> iter = context.selectNodes( path );
        if ( iter != null )
        {
            for ( final Object obj : iter )
            {
                result.add( (Node) obj );
            }
        }

        return result;
    }

    /**
     * Select the ordered list of text values for nodes matching the given XPath expression.
     * <br/>
     * If cachePath is true, compile the XPath expression and cache for future use. Use this if the expression
     * isn't too specific, and will be used often. Don't traverse deeper into ancestry documents farther than
     * maxAncestry. If maxAncestry < 0, traverse all ancestors.
     */
    public List<String> resolveXPathToAggregatedStringList( final String path, final boolean cachePath,
                                                            final int maxAncestry, final boolean includeMixins )
    {
        final List<Node> nodes = resolveXPathToAggregatedNodeList( path, cachePath, maxAncestry, includeMixins );
        final List<String> result = new ArrayList<String>( nodes.size() );
        for ( final Node node : nodes )
        {
            if ( node != null )
            {
                final String txt = node.getTextContent();
                if ( txt != null )
                {
                    result.add( txt.trim() );
                }
            }
        }

        return result;
    }

    /**
     * Select the ordered list of text values for nodes matching the given XPath expression, rooted in the 
     * given context.
     * <br/>
     * If cachePath is true, compile the XPath expression and cache for future use. Use this if the expression
     * isn't too specific, and will be used often.
     */
    public List<String> resolveXPathToAggregatedStringListFrom( final JXPathContext context, final String path,
                                                                final boolean cachePath )
    {
        final List<Node> nodes = resolveXPathToNodeListFrom( context, path, cachePath );
        final List<String> result = new ArrayList<String>( nodes.size() );
        for ( final Node node : nodes )
        {
            if ( node != null )
            {
                final String txt = node.getTextContent();
                if ( txt != null )
                {
                    result.add( txt.trim() );
                }
            }
        }

        return result;
    }

    /**
     * Select the first node matching the given XPath expression, rooted in the given context. If cachePath 
     * is true, compile the XPath expression and cache for future use. This is useful if the expression isn't
     * overly specific, and will be used multiple times.
     */
    protected synchronized Node resolveXPathToNodeFrom( final JXPathContext context, final String path,
                                                        final boolean cachePath )
    {
        return (Node) context.selectSingleNode( path );
    }

    /**
     * Select the ordered list of nodes matching the given XPath expression, rooted in the given context. If 
     * cachePath is true, compile the XPath expression and cache for future use. This is useful if the
     * expression isn't overly specific, and will be used multiple times.
     */
    public synchronized List<Node> resolveXPathToNodeListFrom( final JXPathContext context, final String path,
                                                               final boolean cachePath )
        throws GalleyMavenRuntimeException
    {
        final List<Node> result = new ArrayList<Node>();
        final List<?> iter = context.selectNodes( path );
        if ( iter != null )
        {
            for ( final Object obj : iter )
            {
                result.add( (Node) obj );
            }
        }

        return result;
    }

    /**
     * Retrieve the ordered list of mix-in documents that can be searched for nodes and values (in addition
     * to the local XML document).
     */
    public List<MavenXmlMixin<T>> getMixins()
    {
        return mixins;
    }

    /**
     * Append the specified mix-in for consideration when searching for nodes and values.
     */
    public void addMixin( final MavenXmlMixin<T> mixin )
    {
        mixins.add( mixin );
    }

    /**
     * Remove the specified mix-in from consideration when searching for nodes and values.
     */
    public void removeMixin( final MavenXmlMixin<T> mixin )
    {
        mixins.remove( mixin );
    }

    /**
     * Render the given element to an XML string.
     */
    public String toXML( final Element element )
    {
        return toXML( element, true );
    }

    /**
     * Render the given element to an XML string, with control over whether xml declaration is added.
     */
    protected String toXML( final Element element, boolean printXmlDeclaration )
    {
        return xml.toXML( element, printXmlDeclaration );
    }

}
