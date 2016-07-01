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

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFunction;
import javax.xml.xpath.XPathFunctionException;
import javax.xml.xpath.XPathFunctionResolver;

import org.w3c.dom.NodeList;

@ApplicationScoped
public class XPathManager
{

    public static final String ID = "id";

    public static final String G = "groupId";

    public static final String A = "artifactId";

    public static final String V = "version";

    public static final String T = "type";

    public static final String C = "classifier";

    public static final String AND = " and ";

    public static final String OR = " or ";

    public static final String NOT = " not(";

    public static final String OPEN_PAREN = "(";

    public static final String END_PAREN = ")";

    public static final String RESOLVE = "ext:resolve(";

    public static final String TEXT = "/text()";

    public static final String EQQUOTE = "=\"";

    public static final String QUOTE = "\"";

    private final XPath xpath;

    //    private final Map<String, WeakReference<XPathExpression>> xpaths = new HashMap<>();

    public XPathManager()
    {
        this.xpath = XPathFactory.newInstance()
                                 .newXPath();
        this.xpath.setXPathFunctionResolver( new TLFunctionResolver() );
    }

    public synchronized void clear()
    {
        //        xpaths.clear();
    }

    public XPathExpression getXPath( final String path, final boolean cache )
        throws XPathExpressionException
    {
        XPathExpression expression = null;
        //        if ( cache )
        //        {
        //            synchronized ( this )
        //            {
        //                final WeakReference<XPathExpression> ref = xpaths.get( path );
        //                if ( ref != null )
        //                {
        //                    expression = ref.get();
        //                }
        //
        //                if ( expression == null )
        //                {
        //                    expression = xpath.compile( path );
        //                    xpaths.put( path, new WeakReference<XPathExpression>( expression ) );
        //                }
        //            }
        //        }
        //        else
        //        {
        expression = xpath.compile( path );
        //        }

        return expression;
    }

    public static final class TLFunctionResolver
        implements XPathFunctionResolver
    {

        private static InheritableThreadLocal<MavenPomView> pomView = new InheritableThreadLocal<MavenPomView>();

        public static void setPomView( final MavenPomView pom )
        {
            pomView.set( pom );
        }

        public static MavenPomView getPomView()
        {
            return pomView.get();
        }

        @Override
        public XPathFunction resolveFunction( final QName functionName, final int arity )
        {
            if ( functionName.getLocalPart()
                             .equals( "resolve" ) )
            {
                return new ResolveFunction( pomView.get() );
            }

            return null;
        }

    }

    private static final class ResolveFunction
        implements XPathFunction
    {

        private final MavenPomView pom;

        public ResolveFunction( final MavenPomView pom )
        {
            this.pom = pom;
        }

        @Override
        public Object evaluate( @SuppressWarnings( "rawtypes" ) final List args )
            throws XPathFunctionException
        {
            if ( args.isEmpty() )
            {
                return null;
            }

            final NodeList val = (NodeList) args.get( 0 );
            if ( val == null || val.getLength() != 1 )
            {
                return null;
            }

            final String value = val.item( 0 )
                                    .getTextContent();

            //            logger.info( "FUNC: resolving: '{}' with pom: {}", value, pom );

            final String result = pom.resolveExpressions( value );

            //            logger.info( "FUNC: resolve result: '{}' with pom: {}", result, pom );

            return result;
        }

    }

}
