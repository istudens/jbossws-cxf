/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.wsf.stack.cxf.deployment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.LinkedList;
import java.util.List;

import javax.wsdl.Definition;
import javax.wsdl.WSDLException;
import javax.wsdl.xml.WSDLWriter;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.cxf.Bus;
import org.apache.cxf.service.model.ServiceInfo;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.cxf.wsdl.WSDLManager;
import org.apache.cxf.wsdl11.ServiceWSDLBuilder;
import org.jboss.ws.common.utils.AbstractWSDLFilePublisher;
import org.jboss.wsf.spi.deployment.ArchiveDeployment;
import org.jboss.wsf.stack.cxf.Loggers;
import org.jboss.wsf.stack.cxf.Messages;
import org.w3c.dom.Document;

/**
 * A WSDL file publisher for CXF based stack
 * 
 * @author alessio.soldano@jboss.com
 * @since 25-Mar-2010
 *
 */
public class WSDLFilePublisher extends AbstractWSDLFilePublisher
{
   public WSDLFilePublisher(ArchiveDeployment dep)
   {
      super(dep);
   }
   
   /** Publish the deployed wsdl file to the data directory
    */
   public void publishWsdlFiles(QName serviceName, String wsdlLocation, Bus bus, List<ServiceInfo> serviceInfos) throws IOException
   {
      String deploymentName = dep.getCanonicalName();
      File wsdlFile = getPublishLocation(serviceName.getLocalPart(), deploymentName, wsdlLocation);
      wsdlFile.getParentFile().mkdirs();
      try
      {
         // Write the wsdl def to file
         ServiceWSDLBuilder builder = new ServiceWSDLBuilder(bus, serviceInfos);
         Definition def = builder.build();

         Document doc = getWsdlDocument(bus, def);
         writeDocument(doc, wsdlFile);

         URL wsdlPublishURL = new URL(URLDecoder.decode(wsdlFile.toURI().toURL().toExternalForm(), "UTF-8"));
         Loggers.DEPLOYMENT_LOGGER.wsdlFilePublished(wsdlPublishURL);

         // Process the wsdl imports
         if (def != null)
         {
            List<String> published = new LinkedList<String>();
            String expLocation = getExpLocation(wsdlLocation);
            publishWsdlImports(wsdlPublishURL, def, published, expLocation);

            // Publish XMLSchema imports
            publishSchemaImports(wsdlPublishURL, doc.getDocumentElement(), published, expLocation);
         }
         else
         {
            throw Messages.MESSAGES.wsdl20NotSupported();
         }
      }
      catch (RuntimeException rte)
      {
         throw rte;
      }
      catch (Exception e)
      {
         throw Messages.MESSAGES.cannotPublishWSDLTo(serviceName, wsdlFile, e);
      }
   }
   
   private static Document getWsdlDocument(Bus bus, Definition def) throws WSDLException
   {
      WSDLWriter wsdlWriter = bus.getExtension(WSDLManager.class).getWSDLFactory().newWSDLWriter();
      def.setExtensionRegistry(bus.getExtension(WSDLManager.class).getExtensionRegistry());
      return wsdlWriter.getDocument(def);
   }
   
   private static void writeDocument(Document doc, File file) throws IOException, XMLStreamException
   {
      String enc = null;
      try
      {
         enc = doc.getXmlEncoding();
      }
      catch (Exception ex)
      {
         //ignore - not dom level 3
      }
      if (enc == null)
      {
         enc = "utf-8";
      }
      FileOutputStream fos = new FileOutputStream(new File(file.toURI()));
      try
      {
         XMLStreamWriter writer = StaxUtils.createXMLStreamWriter(fos, enc);
         StaxUtils.writeNode(doc, writer, true);
         writer.flush();
      }
      finally
      {
         fos.close();
      }
   }
   
   /**
    * Get the file publish location
    */
   private File getPublishLocation(String serviceName, String archiveName, String wsdlLocation) throws IOException
   {
      if (wsdlLocation == null && serviceName == null)
      {
         Loggers.DEPLOYMENT_LOGGER.cannotGetWsdlPublishLocation();
         return null;
      }

      //JBWS-2829: windows issue
      if (archiveName.startsWith("http://"))
      {
         archiveName = archiveName.replace("http://", "http-");
      }

      File locationFile = new File(serverConfig.getServerDataDir().getCanonicalPath() + "/wsdl/" + archiveName);

      File result;
      if (wsdlLocation != null && wsdlLocation.indexOf(expLocation) >= 0)
      {
         wsdlLocation = wsdlLocation.substring(wsdlLocation.indexOf(expLocation) + expLocation.length());
         result = new File(locationFile + "/" + wsdlLocation);
      }
      else if (wsdlLocation != null && (wsdlLocation.startsWith("vfsfile:") || wsdlLocation.startsWith("file:") || wsdlLocation.startsWith("jar:") || wsdlLocation.startsWith("vfszip:")))
      {
         wsdlLocation = wsdlLocation.substring(wsdlLocation.lastIndexOf("/") + 1);
         result = new File(locationFile + "/" + wsdlLocation);
      }
      else
      {
         result = new File(locationFile + "/" + serviceName + ".wsdl");
      }

      return result;
   }
   
   private String getExpLocation(String wsdlLocation) {
      if (wsdlLocation == null || wsdlLocation.indexOf(expLocation) >= 0) {
         return expLocation;
      } else { //JBWS-3540
         return wsdlLocation.contains("/") ? wsdlLocation.substring(0, wsdlLocation.lastIndexOf("/") + 1) : "";
      }
   }
}