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

import org.apache.commons.jxpath.JXPathContext;
import org.codehaus.plexus.interpolation.InterpolationException;
import org.codehaus.plexus.interpolation.Interpolator;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;
import org.codehaus.plexus.interpolation.ValueSource;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.VersionlessArtifactRef;
import org.commonjava.maven.galley.maven.GalleyMavenException;
import org.commonjava.maven.galley.maven.GalleyMavenRuntimeException;
import org.commonjava.maven.galley.maven.parse.JXPathUtils;
import org.commonjava.maven.galley.maven.parse.MavenPomReader;
import org.commonjava.maven.galley.maven.parse.ResolveFunctions;
import org.commonjava.maven.galley.maven.parse.XMLInfrastructure;
import org.commonjava.maven.galley.maven.spi.defaults.MavenPluginDefaults;
import org.commonjava.maven.galley.maven.spi.defaults.MavenPluginImplications;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.commonjava.maven.galley.maven.model.view.XPathManager.V;

/**
 * Represents a view of a Maven POM which has had inheritance, mix-ins (eg. BOMs), and expressions resolved.
 * This is really a wrapper around an ordered structure of XML documents which constitutes a fully
 * materialized POM, and which provides convenience methods to construct child XML views that correspond to
 * the model fields in a POM file.
 *
 * @author jdcasey
 */
public class MavenPomView
    extends MavenXmlView<ProjectVersionRef>
{

    public static final String ALL_PROFILES = "*";

    private static final String EXPRESSION_PATTERN = ".*\\$\\{.+\\}.*";

    private static final String TEXT_SUFFIX = "/text()";

    private final ProjectVersionRef versionedRef;

    private final MavenPluginDefaults pluginDefaults;

    private final MavenPluginImplications pluginImplications;

    private final Set<String> activeProfileIds;

    /**
     * Not really designed for direct construction. See {@link MavenPomReader}.
     *
     * @param ref The GAV represented by this view
     * @param stack The stack of XML documents that constitutes the inheritance hierarchy for this POM. Does
     *   NOT contain mix-ins like BOMs, which are added later in the view init process.
     * @param xpath Cache manager for XPath objects that have been compiled for this view
     * @param pluginDefaults Provides versions and groupId's when a plugin declaration doesn't specify one.
     * @param pluginImplications Provides extra dependencies expected when a given plugin is encountered
     * @param xml Infrastructure methods for parsing/generating XML
     * @param activeProfileIds When querying for model elements, consider the given profiles to have been
     *   merged in (activated)
     *
     * @see MavenPomReader
     */
    public MavenPomView( final ProjectVersionRef ref, final List<DocRef<ProjectVersionRef>> stack,
                         final XPathManager xpath, final MavenPluginDefaults pluginDefaults,
                         final MavenPluginImplications pluginImplications, final XMLInfrastructure xml,
                         final String... activeProfileIds )
    {
        // define what xpaths are not inheritable...
        super( stack, xpath, xml, "/project/parent", "/project/artifactId" );

        this.pluginImplications = pluginImplications;
        this.activeProfileIds = new HashSet<String>( Arrays.asList( activeProfileIds ) );

        if ( stack.isEmpty() )
        {
            throw new IllegalArgumentException( "Cannot create a POM view with no POMs!" );
        }

        this.versionedRef = ref;
        this.pluginDefaults = pluginDefaults;
    }

    /**
     * Return the list of profile id's that were considered active when this view was constructed. Their
     * model elements will be returned as if they were declared in the main POM body, just as actual activated
     * profiles will be during a Maven build.
     */
    public Set<String> getActiveProfileIds()
    {
        return activeProfileIds.contains( ALL_PROFILES ) ? getProfileIds() : activeProfileIds;
    }

    /**
     * Retrieve id's for all available profiles in the inheritance hierarchy.
     */
    public Set<String> getProfileIds()
    {
        return new HashSet<String>( resolveXPathExpressionToAggregatedList( "/profiles/profile/id/text()", false, -1, false ) );
    }

    public String resolveMavenExpression( final String expression, final String... activeProfileIds )
        throws GalleyMavenException
    {
        String expr = expression.replace( '.', '/' );
        if ( expr.startsWith( "pom" ) )
        {
            expr = "/project/" + expr.substring( 4 );
        }

        if ( !expr.startsWith( "/" ) )
        {
            expr = "/" + expr;
        }

        if ( !expr.startsWith( "/project" ) )
        {
            expr = "/project" + expr;
        }

        String value = resolveXPathExpression( expr, true, -1 );

        for ( int i = 0; value == null && activeProfileIds != null && i < activeProfileIds.length; i++ )
        {
            final String profileId = activeProfileIds[i];
            value =
                resolveXPathExpression( "//profile[id/text()=\"" + profileId + "\"]/properties/" + expression, true,
                                        -1, activeProfileIds );
        }

        if ( value == null )
        {
            final List<Node> propertyNodes = resolveXPathToAggregatedNodeList( "/project/properties/*", true, -1, false );
            for ( final Node propertyNode : propertyNodes )
            {
                if ( expression.equals( propertyNode.getNodeName() ) )
                {
                    value = propertyNode.getTextContent()
                                        .trim();
                    break;
                }
            }
        }

        return value;
    }

    /**
     * Return the GAV for this POM view
     */
    public synchronized ProjectVersionRef asProjectVersionRef()
    {
        return versionedRef;
    }

    /**
     * Return the groupId of this POM. If not declared directly, try to infer from the parent element.
     */
    public String getGroupId()
        throws GalleyMavenException
    {
        return asProjectVersionRef().getGroupId();
    }

    /**
     * Return the artifactId of this POM. Parent values are not considered, since this element is required
     * to be declared locally in order for the POM to be valid.
     */
    public String getArtifactId()
        throws GalleyMavenException
    {
        return asProjectVersionRef().getArtifactId();
    }

    /**
     * Return the version of this POM. If not declared directly, try to infer from the parent element.
     */
    public String getVersion()
        throws GalleyMavenException
    {
        return asProjectVersionRef().getVersionString();
    }

    /**
     * Find the id of the profile that contains the given element, if there is one. Otherwise, return null.
     */
    public String getProfileIdFor( final Element element )
    {
        Node parent = element;
        if ( parent != null )
        {
            do
            {
                parent = parent.getParentNode();
            }
            while ( parent != null && !"profile".equals( parent.getNodeName() ) );
        }

        if ( parent == null )
        {
            return null;
        }

        return (String) JXPathUtils.newContext( parent )
                                   .getValue( "id" );
    }

    /**
     * Resolve an ordered list of {@link DependencyView} instances for non-managed dependencies that are
     * either in the main POM body or in an active profile.
     */
    public List<DependencyView> getAllDirectDependencies()
        throws GalleyMavenException
    {
        final String xp =
            "//dependency[not(ancestor::dependencyManagement) and not(ancestor::build) and not(ancestor::reporting)]";
        //        final String xp = "./project/dependencies/dependency";
        final List<MavenPomElementView> depNodes = resolveXPathToAggregatedElementViewList( xp, true, -1 );
        final List<DependencyView> depViews = new ArrayList<DependencyView>( depNodes.size() );
        for ( final MavenPomElementView node : depNodes )
        {
            depViews.add( new DependencyView( node.getPomView(), node.getElement(), node.getOriginInfo() ) );
        }

        return depViews;
    }

    /**
     * Reads all managed dependencies from this pom. It means all from pom
     * itself, from its parents and also from imported BOM poms. This method will even include colliding dependency declarations, or overlaps.
     *
     * @return list of read dependencies
     */
    public List<DependencyView> getAllManagedDependenciesUnfiltered()
    {
        final List<MavenPomElementView> depNodes =
            resolveXPathToAggregatedElementViewList( "//dependencyManagement/dependencies/dependency[not(scope/text()=\"import\")]",
                                                     true, -1 );
        final List<DependencyView> depViews = new ArrayList<DependencyView>( depNodes.size() );
        for ( final MavenPomElementView node : depNodes )
        {
            depViews.add( new DependencyView( node.getPomView(), node.getElement(), node.getOriginInfo() ) );
        }

        return depViews;
    }

    /**
     * Reads all managed dependencies from this pom. It means all from pom
     * itself, from its parents and also from imported BOM poms. This method will eliminate overlapping, or colliding, dependency declarations.
     *
     * @return list of read dependencies
     */
    public List<DependencyView> getAllManagedDependencies()
        throws GalleyMavenException
    {
        final List<DependencyView> raw = getAllManagedDependenciesUnfiltered();
        final List<DependencyView> depViews = new ArrayList<DependencyView>( raw.size() );
        final Set<VersionlessArtifactRef> seen = new HashSet<VersionlessArtifactRef>();
        for ( final DependencyView dv : raw )
        {
            final VersionlessArtifactRef var = dv.asVersionlessArtifactRef();
            if ( !seen.contains( var ) )
            {
                depViews.add( dv );
                seen.add( var );
            }
        }

        return depViews;
    }

    /**
     * Reads managed dependencies specified in this pom itself and its parents.
     * It omits managed dependencies specified in imported BOM poms.
     *
     * @return list of read dependencies
     */
    public List<DependencyView> getManagedDependenciesNoImports()
    {
        final List<MavenPomElementView> depNodes =
            resolveXPathToAggregatedElementViewList( "//dependencyManagement/dependencies/dependency[not(scope/text()=\"import\")]",
                                                     true, -1, false );
        final List<DependencyView> depViews = new ArrayList<DependencyView>( depNodes.size() );
        for ( final MavenPomElementView node : depNodes )
        {
            depViews.add( new DependencyView( node.getPomView(), node.getElement(), node.getOriginInfo() ) );
        }

        return depViews;
    }

    /**
     * Return all managed, import-scoped, pom-typed dependency declarations in this POM
     * (also considering inheritance and applied mix-in documents, such as applied BOMs). Return an ordered
     * list of {@link DependencyView} instances referencing each BOM encountered.
     */
    public List<DependencyView> getAllBOMs()
        throws GalleyMavenException
    {
        final List<MavenPomElementView> depNodes =
            resolveXPathToAggregatedElementViewList( "//dependencyManagement/dependencies/dependency[type/text()=\"pom\" and scope/text()=\"import\"]",
                                                     true, -1 );

        final List<DependencyView> depViews = new ArrayList<DependencyView>( depNodes.size() );
        for ( final MavenPomElementView node : depNodes )
        {
            depViews.add( new DependencyView( node.getPomView(), node.getElement(), node.getOriginInfo() ) );
        }

        return depViews;
    }

    /**
     * RAW ACCESS: Retrieve an {@link Element} instance for the first element matching the given
     * XPath expression and within the other given parameters. If none is found, return null.
     * The XPath instance compiled from the given expression will be cached for future use. This method will
     * consider values available in mix-ins (eg. BOMs).
     *
     * @param path The XPath expression to resolve
     * @param localOnly If true, don't consider values present in ancestry.
     */
    public Element resolveXPathToElement( final String path, final boolean localOnly )
        throws GalleyMavenException
    {
        final Node node = resolveXPathToNode( path, true, localOnly ? 0 : -1 );
        if ( node != null && node.getNodeType() == Node.ELEMENT_NODE )
        {
            return (Element) node;
        }

        return null;
    }

    /**
     * RAW ACCESS: Retrieve a {@link MavenPomElementView} instance for the first element matching the given
     * XPath expression and within the other given parameters. If none is found, return null.
     *
     * @param path The XPath expression to resolve
     * @param cachePath Whether to cache the compiled XPath instance. Do this if the expression isn't overly
     *   specific, and will be used multiple times.
     * @param maxDepth If a large ancestry (parents of parents of...) exists, limit the search to the given
     *   depth
     */
    public MavenPomElementView resolveXPathToElementView( final String path, final boolean cachePath, final int maxDepth )
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
        Element n = null;
        for ( final DocRef<ProjectVersionRef> dr : stack )
        {
            if ( maxAncestry > -1 && ancestryDepth > maxAncestry )
            {
                break;
            }

            final MavenPomView oldView = ResolveFunctions.getPomView();
            try
            {
                ResolveFunctions.setPomView( this );
                n = (Element) dr.getDocContext()
                                .selectSingleNode( path );
            }
            finally
            {
                ResolveFunctions.setPomView( oldView );
            }
            //                logger.info( "Value of '{}' at depth: {} is: {}", path, ancestryDepth, result );

            if ( n != null )
            {
                break;
            }

            ancestryDepth++;
        }

        if ( n != null )
        {
            return new MavenPomElementView( this, n, new OriginInfo( ancestryDepth != 0 ) );
        }

        MavenPomElementView result = null;
        for ( final MavenXmlMixin<ProjectVersionRef> mixin : mixins )
        {
            if ( mixin.matches( path ) )
            {
                final MavenPomView mixinView = (MavenPomView) mixin.getMixin();
                result = mixinView.resolveXPathToElementView( path, true, maxAncestry );
                //                        logger.info( "Value of '{}' in mixin: {} is: '{}'", path, mixin );
            }

            if ( result != null )
            {
                return result;
            }
        }

        return null;
    }

    /**
     * Wrap the given DOM element in a {@link DependencyView} instance, to allow convenience methods to query
     * the dependency information.
     */
    public DependencyView asDependency( final Element depElement )
    {
        return asDependency( depElement, new OriginInfo() );
    }

    /**
     * Wrap the given DOM element in a {@link DependencyView} instance, to allow convenience methods to query
     * the dependency information.
     */
    public DependencyView asDependency( final Element depElement, final OriginInfo originInfo )
    {
        return new DependencyView( this, depElement, originInfo );
    }

    /**
     * Wrap the given DOM element in a {@link PluginView} instance, to allow convenience methods to query
     * the plugin information.
     */
    public PluginView asPlugin( final Element element )
    {
        return asPlugin( element, new OriginInfo() );
    }

    /**
     * Wrap the given DOM element in a {@link PluginView} instance, to allow convenience methods to query
     * the plugin information.
     */
    public PluginView asPlugin( final Element element, final OriginInfo originInfo )
    {
        return new PluginView( this, element, originInfo, pluginDefaults, pluginImplications );
    }

    /**
     * Return a {@link ParentView} instance wrapping the parent XML section, if it exists. Return null
     * otherwise.
     */
    public ParentView getParent()
        throws GalleyMavenException
    {
        final Element parentEl = resolveXPathToElement( "/project/parent", true );

        if ( parentEl != null )
        {
            return new ParentView( this, parentEl, new OriginInfo() );
        }

        return null;
    }

    /**
     * Return an ordered list of {@link ExtensionView} instances corresponding to the build extensions
     * declared in this POM (also considering inheritance and applied mix-in documents, such as applied BOMs).
     */
    public List<ExtensionView> getBuildExtensions()
        throws GalleyMavenException
    {
        final List<MavenPomElementView> list =
            resolveXPathToAggregatedElementViewList( "/project//build/extensions/extension", true, -1 );
        final List<ExtensionView> result = new ArrayList<ExtensionView>( list.size() );
        for ( final MavenPomElementView node : list )
        {
            if ( node == null )
            {
                continue;
            }

            result.add( new ExtensionView( node.getPomView(), node.getElement(), node.getOriginInfo() ) );
        }

        return result;
    }

    /**
     * Return an ordered list of {@link PluginView} instances for plugin declarations matching the given
     * XPath expression (also considering inheritance and applied mix-in documents, such as applied BOMs).
     */
    public List<PluginView> getAllPluginsMatching( final String path )
        throws GalleyMavenException
    {
        final List<MavenPomElementView> list = resolveXPathToAggregatedElementViewList( path, true, -1 );
        final List<PluginView> result = new ArrayList<PluginView>( list.size() );
        for ( final MavenPomElementView node : list )
        {
            if ( node == null )
            {
                continue;
            }

            result.add( new PluginView( node.getPomView(), node.getElement(), node.getOriginInfo(), pluginDefaults, pluginImplications ) );
        }

        return result;
    }

    /**
     * Return an ordered list of {@link DependencyView} instances for dependency declarations matching the given
     * XPath expression (also considering inheritance and applied mix-in documents, such as applied BOMs).
     */
    public List<DependencyView> getAllDependenciesMatching( final String path )
        throws GalleyMavenException
    {
        final List<MavenPomElementView> list = resolveXPathToAggregatedElementViewList( path, true, -1 );
        final List<DependencyView> result = new ArrayList<DependencyView>( list.size() );
        for ( final MavenPomElementView node : list )
        {
            if ( node == null )
            {
                continue;
            }

            result.add( new DependencyView( node.getPomView(), node.getElement(), node.getOriginInfo() ) );
        }

        return result;
    }

    /**
     * Return an ordered list of {@link PluginView} instances for declared, non-managed plugins (also
     * considering inheritance and applied mix-in documents, such as applied BOMs).
     */
    public List<PluginView> getAllBuildPlugins()
        throws GalleyMavenException
    {
        final List<MavenPomElementView> list =
            resolveXPathToAggregatedElementViewList( "/project//build/plugins/plugin", true, -1 );
        final List<PluginView> result = new ArrayList<PluginView>( list.size() );
        for ( final MavenPomElementView node : list )
        {
            if ( node == null )
            {
                continue;
            }

            result.add( new PluginView( node.getPomView(), node.getElement(), node.getOriginInfo(), pluginDefaults, pluginImplications ) );
        }

        return result;
    }

    /**
     * Return an ordered list of {@link PluginView} instances for declared, managed plugins (also
     * considering inheritance and applied mix-in documents, such as applied BOMs).
     */
    public List<PluginView> getAllManagedBuildPlugins()
        throws GalleyMavenException
    {
        final List<MavenPomElementView> list =
            resolveXPathToAggregatedElementViewList( "/project//pluginManagement/plugins/plugin", true, -1 );

        final List<PluginView> result = new ArrayList<PluginView>( list.size() );
        for ( final MavenPomElementView node : list )
        {
            if ( node == null )
            {
                continue;
            }

            result.add( new PluginView( node.getPomView(), node.getElement(), node.getOriginInfo(), pluginDefaults, pluginImplications ) );
        }

        return result;
    }

    public List<RepositoryView> getAllRepositories()
    {
        final List<MavenPomElementView> list =
            resolveXPathToAggregatedElementViewList( "/project//repositories/repository", true, -1 );

        final List<RepositoryView> result = new ArrayList<RepositoryView>( list.size() );
        for ( final MavenPomElementView node : list )
        {
            if ( node == null )
            {
                continue;
            }

            result.add( new RepositoryView( node.getPomView(), node.getElement(), node.getOriginInfo() ) );
        }

        return result;
    }

    public List<RepositoryView> getAllPluginRepositories()
    {
        final List<MavenPomElementView> list =
            resolveXPathToAggregatedElementViewList( "/project//pluginRepositories/pluginRepository", true, -1 );

        final List<RepositoryView> result = new ArrayList<RepositoryView>( list.size() );
        for ( final MavenPomElementView node : list )
        {
            if ( node == null )
            {
                continue;
            }

            result.add( new RepositoryView( node.getPomView(), node.getElement(), node.getOriginInfo() ) );
        }

        return result;
    }

    public List<RepositoryView> getActiveRepositories()
    {
        final List<MavenPomElementView> list =
            resolveXPathToAggregatedElementViewList( "/project//repositories/repository", true, -1 );

        final List<RepositoryView> result = new ArrayList<RepositoryView>( list.size() );
        for ( final MavenPomElementView node : list )
        {
            if ( node == null )
            {
                continue;
            }

            if ( activeProfileIds.contains( ALL_PROFILES )
                || activeProfileIds.contains( getProfileIdFor( node.getElement() ) ) )
            {
                result.add( new RepositoryView( node.getPomView(), node.getElement(), node.getOriginInfo() ) );
            }
        }

        return result;
    }

    public List<RepositoryView> getActivePluginRepositories()
    {
        final List<MavenPomElementView> list =
            resolveXPathToAggregatedElementViewList( "/project//pluginRepositories/pluginRepository", true, -1 );

        final List<RepositoryView> result = new ArrayList<RepositoryView>( list.size() );
        for ( final MavenPomElementView node : list )
        {
            if ( node == null )
            {
                continue;
            }

            if ( activeProfileIds.contains( ALL_PROFILES )
                || activeProfileIds.contains( getProfileIdFor( node.getElement() ) ) )
            {
                result.add( new RepositoryView( node.getPomView(), node.getElement(), node.getOriginInfo() ) );
            }
        }

        return result;
    }

    /**
     * Return the ordered list of GAV references ({@link ProjectVersionRefView} instances) from this POM that
     * match the given XPath expression (also considering inheritance and applied mix-in documents, such as
     * applied BOMs).
     */
    public List<ProjectVersionRefView> getProjectVersionRefs( final String path )
        throws GalleyMavenException
    {
        final List<MavenPomElementView> list = resolveXPathToAggregatedElementViewList( path, true, -1 );
        final List<ProjectVersionRefView> result = new ArrayList<ProjectVersionRefView>( list.size() );
        for ( final MavenPomElementView node : list )
        {
            if ( node == null )
            {
                continue;
            }

            result.add( new MavenGAVView( node.getPomView(), node.getElement(), node.getOriginInfo() ) );
        }

        return result;
    }

    /**
     * Return the ordered list of GA (project) references ({@link ProjectRefView} instances) from this POM that
     * match the given XPath expression (also considering inheritance and applied mix-in documents, such as
     * applied BOMs).
     */
    public List<ProjectRefView> getProjectRefs( final String path )
        throws GalleyMavenException
    {
        final List<MavenPomElementView> list = resolveXPathToAggregatedElementViewList( path, true, -1 );
        final List<ProjectRefView> result = new ArrayList<ProjectRefView>( list.size() );
        for ( final MavenPomElementView node : list )
        {
            if ( node == null )
            {
                continue;
            }

            final String v = node.getValue( V );
            if ( v != null )
            {
                result.add( new MavenGAVView( this, (Element) node, node.getOriginInfo() ) );
            }
            else
            {
                result.add( new MavenGAView( this, (Element) node, node.getOriginInfo() ) );
            }
        }

        return result;
    }


    public PropertiesView getProperties()
            throws GalleyMavenException
    {
        Element localProps = resolveXPathToElement( "/project/properties", true );
        return new PropertiesView( this, localProps, new OriginInfo( false ) );
    }

    /**
     * RAW ACCESS: Retrieve an ordered list of {@link MavenPomElementView} instances matching the given XPath
     * expression and within the other given parameters. Any available mix-in documents (eg. BOMs) will also
     * be searched.
     *
     * @param path The XPath expression to resolve
     * @param cachePath Whether to cache the compiled XPath instance. Do this if the expression isn't overly
     *   specific, and will be used multiple times.
     * @param maxDepth If a large ancestry (parents of parents of...) exists, limit the search to the given
     *   depth
     *
     * @see {@link #resolveXPathToAggregatedElementViewList(String, boolean, int, boolean)}
     */
    public List<MavenPomElementView> resolveXPathToAggregatedElementViewList( final String path,
                                                                              final boolean cachePath,
                                                                              final int maxDepth )
    {
        return resolveXPathToAggregatedElementViewList( path, cachePath, maxDepth, true );
    }

    /**
     * RAW ACCESS: Retrieve an ordered list of {@link MavenPomElementView} instances matching the given XPath
     * expression and within the other given parameters.
     *
     * @param path The XPath expression to resolve
     * @param cachePath Whether to cache the compiled XPath instance. Do this if the expression isn't overly
     *   specific, and will be used multiple times.
     * @param maxDepth If a large ancestry (parents of parents of...) exists, limit the search to the given
     *   depth
     * @param includeMixins Whether to include mix-ins (eg. BOMs) when searching for matches
     */
    public synchronized List<MavenPomElementView> resolveXPathToAggregatedElementViewList( final String path,
                                                                                           final boolean cachePath,
                                                                                           final int maxDepth,
                                                                                           final boolean includeMixins )
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
        final List<MavenPomElementView> result = new ArrayList<MavenPomElementView>();
        for ( final DocRef<ProjectVersionRef> dr : stack )
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
                    result.add( new MavenPomElementView( this, (Element) node, new OriginInfo( ancestryDepth != 0 ) ) );
                }
            }

            ancestryDepth++;
        }

        if ( includeMixins )
        {
            for ( final MavenXmlMixin<ProjectVersionRef> mixin : mixins )
            {
                if ( !mixin.matches( path ) )
                {
                    continue;
                }

                final MavenPomView mixinView = (MavenPomView) mixin.getMixin();
                final List<MavenPomElementView> nodes =
                    mixinView.resolveXPathToAggregatedElementViewList( path, cachePath, maxAncestry, includeMixins );
                if ( nodes != null )
                {
                    for ( final MavenPomElementView node : nodes )
                    {
                        node.getOriginInfo().setMixin( true );
                        result.add( node );
                    }
                }
            }
        }

        return result;
    }

    /**
     * RAW ACCESS: Resolve the given XPath expression. If the result is a Node, retrieve the text() child. Resolve any
     * Maven-style expressions in the result, and pass back the resolved value, or null if nothing is matched.
     * If more than one node matches, process the first for its string value.
     */
    public String resolveXPathExpression( final String path, final boolean cachePath, final int maxAncestry,
                                          final String... activeProfileIds )
    {
        final String p = trimTextSuffix( path );

        final String raw = resolveXPathToRawString( p, cachePath, maxAncestry );
        if ( raw != null )
        {
            //            logger.info( "Raw content of: '{}' is: '{}'", path, raw );
            return resolveExpressions( raw, activeProfileIds );
        }

        return null;
    }

    private String trimTextSuffix( final String path )
    {
        String p = path;
        if ( p.endsWith( TEXT_SUFFIX ) )
        {
            p = p.substring( 0, p.length() - TEXT_SUFFIX.length() );
        }

        return p;
    }

    /**
     * RAW ACCESS: Resolve the specified XPath expression into a list of Node values, considering values in inheritance
     * and mix-ins (eg. BOMs). Once the node list is found, retrieve the text() of each and resolve any
     * Maven-style expressions. Return the resulting list of strings.
     */
    public List<String> resolveXPathExpressionToAggregatedList( final String path, final boolean cachePath,
                                                                final int maxAncestry, final boolean includeMixins )
    {
        final String p = trimTextSuffix( path );

        final List<Node> nodes = resolveXPathToAggregatedNodeList( p, cachePath, maxAncestry, includeMixins );
        final List<String> result = new ArrayList<String>( nodes.size() );
        for ( final Node node : nodes )
        {
            if ( node != null && node.getNodeType() == Node.TEXT_NODE )
            {
                result.add( resolveExpressions( node.getTextContent()
                                                    .trim() ) );
            }
        }

        return result;
    }

    /**
     * Determine whether the specified value contains a Maven-style expression.
     */
    protected boolean containsExpression( final String value )
    {
        return value != null && value.matches( EXPRESSION_PATTERN );
    }

    /**
     * Apply Maven expression resolution validate to substitute values from this POM into the provided expression.
     *
     * @param value The expression to resolve
     * @param activeProfileIds The profiles to consider active, whose elements should be treated as merged with
     *   the main POM body
     */
    public String resolveExpressions( final String value, final String... activeProfileIds )
    {
        if ( !containsExpression( value ) )
        {
            //            logger.info( "No expressions in: '{}'", value );
            return value;
        }

        synchronized ( this )
        {
            if ( ssi == null )
            {
                ssi = new StringSearchInterpolator();
                ssi.addValueSource( new MavenPomViewVS( this, activeProfileIds ) );
            }
        }

        try
        {
            String result = ssi.interpolate( value );
            //            logger.info( "Resolved '{}' to '{}'", value, result );

            if ( result == null || result.trim()
                                         .length() < 1 )
            {
                result = value;
            }

            return result;
        }
        catch ( final InterpolationException e )
        {
            logger.error( String.format( "Failed to resolve expressions in: '%s'. Reason: %s", value, e.getMessage() ),
                          e );
            return value;
        }
    }

    /**
     * {@link ValueSource} implementation for use in resolving expressions that reference fields in the Maven
     * POM. For use with plexus-interpolation {@link Interpolator} instances.
     */
    private static final class MavenPomViewVS
        implements ValueSource
    {

        //        private final Logger logger = LoggerFactory.getLogger( getClass() );

        private final MavenPomView view;

        private final List<Object> feedback = new ArrayList<Object>();

        private final String[] activeProfileIds;

        public MavenPomViewVS( final MavenPomView view, final String[] activeProfileIds )
        {
            this.view = view;
            this.activeProfileIds = activeProfileIds;
        }

        @Override
        public void clearFeedback()
        {
            feedback.clear();
        }

        @SuppressWarnings( "rawtypes" )
        @Override
        public List getFeedback()
        {
            return feedback;
        }

        @Override
        public Object getValue( final String expr )
        {
            try
            {
                final String value = view.resolveMavenExpression( expr, activeProfileIds );
                //                logger.info( "Value of: '{}' is: '{}'", expr, value );
                return value;
            }
            catch ( final GalleyMavenException e )
            {
                feedback.add( String.format( "Error resolving maven expression: '%s'", expr ) );
                feedback.add( e );
            }

            return null;
        }

    }

}
