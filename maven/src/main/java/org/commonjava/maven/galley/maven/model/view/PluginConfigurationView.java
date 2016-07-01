package org.commonjava.maven.galley.maven.model.view;

import org.w3c.dom.Element;

/**
 * Created by jdcasey on 6/30/16.
 */
public class PluginConfigurationView<T extends AbstractPluginBaseView>
    extends MavenPomElementView
{
    private T parent;

    public PluginConfigurationView( MavenPomView pomView, T parent, Element element, OriginInfo originInfo )
    {
        super( pomView, element, originInfo );
        this.parent = parent;
    }

    public T getParent()
    {
        return parent;
    }

    public String getConfigurationXml()
    {
        return toXML( false );
    }

    public Element getConfigurationElement()
    {
        return getElement();
    }

}
