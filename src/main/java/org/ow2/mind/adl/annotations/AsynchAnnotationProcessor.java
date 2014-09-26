/**
 * Copyright (C) 2014 Schneider-Electric
 *
 * This file is part of "Mind Compiler" is free software: you can redistribute 
 * it and/or modify it under the terms of the GNU Lesser General Public License 
 * as published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact: mind@ow2.org
 *
 * Authors: Julien Tous
 * Contributors: 
 */
package org.ow2.mind.adl.annotations;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Map;

import org.antlr.stringtemplate.StringTemplate;
import org.objectweb.fractal.adl.ADLException;
import org.objectweb.fractal.adl.Definition;
import org.objectweb.fractal.adl.Node;
import org.objectweb.fractal.adl.NodeFactory;
import org.objectweb.fractal.adl.components.ComponentDefinition;
import org.ow2.mind.idl.IDLLoader;
import org.objectweb.fractal.adl.interfaces.Interface;
import org.objectweb.fractal.adl.interfaces.InterfaceContainer;
import org.objectweb.fractal.adl.types.TypeInterface;
import org.ow2.mind.SourceFileWriter;
import org.ow2.mind.adl.annotation.ADLLoaderPhase;
import org.ow2.mind.adl.annotation.AbstractADLLoaderAnnotationProcessor;
import org.ow2.mind.adl.ast.ASTHelper;
import org.ow2.mind.adl.ast.Binding;
import org.ow2.mind.adl.ast.BindingContainer;
import org.ow2.mind.adl.ast.Component;
import org.ow2.mind.adl.ast.ComponentContainer;
import org.ow2.mind.adl.ast.Data;
import org.ow2.mind.adl.ast.DataField;
import org.ow2.mind.adl.ast.DefinitionReference;
import org.ow2.mind.adl.ast.ImplementationContainer;
import org.ow2.mind.adl.ast.MindInterface;
import org.ow2.mind.adl.ast.Source;
import org.ow2.mind.adl.idl.InterfaceDefinitionDecorationHelper;
import org.ow2.mind.annotation.Annotation;
import org.ow2.mind.annotation.AnnotationHelper;
import org.ow2.mind.idl.IDLLocator;
import org.ow2.mind.idl.ast.IDL;
import org.ow2.mind.idl.ast.InterfaceDefinition;
import org.ow2.mind.io.OutputFileLocator;

import com.google.inject.Inject;

/**
 * @author Julien TOUS
 */
public class AsynchAnnotationProcessor
extends
AbstractADLLoaderAnnotationProcessor {

	@Inject
	protected OutputFileLocator   outFileLocator;

	@Inject
	protected IDLLocator   idlLocator;
	
	@Inject
	protected IDLLoader idlLoader;

	@Inject
	protected NodeFactory         nodeFactory;

	//String template that holds the declaration of the private data 
	protected static final String ASYNCH_DATA_TEMPLATE_NAME  = "st.ASYNCHDATA";
	//String template that holds the source code of the to be created component
	protected static final String ASYNCH_SOURCE_TEMPLATE_NAME = "st.ASYNCHSOURCE";
	
	protected DefinitionReference interceptorDefRef = null;
	protected Definition interceptorDef = null;
	protected MindInterface interceptorSrv;
	protected MindInterface interceptorClt;
	protected MindInterface interceptorEventClt;
	
	/**
	 *	Create a interceptor component between source and destination of an asynch binding.
	 */
	public Definition processAnnotation(final Annotation annotation,
			final Node node, final Definition definition, final ADLLoaderPhase phase,
			final Map<Object, Object> context) throws ADLException {
		assert annotation instanceof Asynch;
		final Asynch asynchAnnotation = (Asynch) annotation;
		
		if (node instanceof Binding){
			final Binding asynchBinding = (Binding) node;
			final String clientCompName = asynchBinding.getFromComponent();
			Component clientComp = null;
			final String serverCompName = asynchBinding.getToComponent();
			Component serverComp = null;
			final String schedulerCompName = asynchAnnotation.scheduler;
			Component schedulerComp = null;
			Component[] comps = ((ComponentContainer)definition).getComponents();

			if (comps != null) {
				for (Component comp : comps){
					if (comp.getName().equals(clientCompName)){
						clientComp = comp;
					} else if (comp.getName().equals(serverCompName)){
						serverComp = comp;
					} else if  (comp.getName().equals(schedulerCompName)){
						schedulerComp = comp;
					}
				}
			}
			
			//TODO somehow assert both serverComp and clientComp are populated

			Definition clientCompDef = ASTHelper.getResolvedComponentDefinition(clientComp, loaderItf, context);
			Definition serverCompDef = ASTHelper.getResolvedComponentDefinition(serverComp, loaderItf, context);		
			
			final String clientItfName = asynchBinding.getFromInterface();
			Interface clientItf = null;
			final String serverItfName = asynchBinding.getToInterface();
			Interface serverItf = null;

			Interface[] itfs = ((InterfaceContainer)clientCompDef).getInterfaces();
			if (itfs != null){
				for (Interface itf : itfs){
					if (itf.getName().equals(clientItfName)) {
						clientItf = (Interface) itf;
						break;
					} 
				}
			}
			
			itfs = ((InterfaceContainer)serverCompDef).getInterfaces();
			if (itfs != null){
				for (Interface itf : itfs){
					if (itf.getName().equals(serverItfName)) {
						serverItf = (Interface) itf;
						break;
					}
				}
			}

			//TODO somehow assert both serverItf and clientItf are populated

			final Asynch thisAnnotation = AnnotationHelper.getAnnotation(asynchBinding,	Asynch.class);
			if (thisAnnotation == asynchAnnotation) {
				((BindingContainer) definition).removeBinding(asynchBinding);
				insertInterceptorComp(clientItf, serverItf, clientComp, serverComp, schedulerComp, definition, context);
			}
		}
		return null;
	}

	/**
	 * @throws ADLException 
	 */
	private void insertInterceptorComp(final Interface clientItf, final Interface serverItf,
			final Component clientComp, final Component serverComp, final Component schedulerComp, final Definition definition,
			final Map<Object, Object> context) throws ADLException {
		final InterfaceDefinition itfDef = itfSignatureResolverItf.resolve((TypeInterface) clientItf, definition, context);

		//Name of the instance of the "to be created" interceptor-component
		final String interceptorCompInstName =  clientComp.getName() + "_" + clientItf.getName() + "_to_" + serverComp.getName() + "_" + clientComp.getName() + "_Interceptor";
		//Name of the definition of of the "to be created" proxy-component
		final String interceptorCompName = itfDef.getName() + "_Interceptor";

		//Creating a definition for our interceptor
		createInterceptorDefinition(itfDef,interceptorCompName, context);
		
		//Instantiating and adding a the interceptor-component 
		final Component interceptorComp = ASTHelper.newComponent(nodeFactory, interceptorCompInstName, interceptorDefRef);
		ASTHelper.setResolvedComponentDefinition(interceptorComp, interceptorDef);
		((ComponentContainer) definition).addComponent(interceptorComp);

		//Creating, configuring and adding a binding from interceptor-component to the scheduler
		final Binding schedBinding = ASTHelper.newBinding(nodeFactory);
		schedBinding.setFromComponent(interceptorComp.getName());
		schedBinding.setToComponent(schedulerComp.getName());
		schedBinding.setFromInterface(interceptorEventClt.getName());
		schedBinding.setToInterface("taskIn");
		((BindingContainer) definition).addBinding(schedBinding);

		//Creating, configuring and adding a binding between our new interceptor-component instance and server interface
		final Binding rightBinding = ASTHelper.newBinding(nodeFactory);
		rightBinding.setFromComponent(interceptorComp.getName());
		rightBinding.setToComponent(serverComp.getName());
		rightBinding.setFromInterface(interceptorClt.getName());
		rightBinding.setToInterface(serverItf.getName());
		((BindingContainer) definition).addBinding(rightBinding);

		//Creating, configuring and adding a binding between the client interface and our new interceptor-component instance
		final Binding leftBinding = ASTHelper.newBinding(nodeFactory);
		leftBinding.setToComponent(interceptorComp.getName());
		leftBinding.setFromComponent(clientComp.getName());
		leftBinding.setToInterface(interceptorSrv.getName());
		leftBinding.setFromInterface(clientItf.getName());
		((BindingContainer) definition).addBinding(leftBinding);
	}

	private Definition createInterceptorDefinition(final InterfaceDefinition itfDef, final String interceptorCompName, final Map<Object, Object> context) throws ADLException {

		//Creating the definition
		interceptorDefRef = ASTHelper.newDefinitionReference(nodeFactory, interceptorCompName);
		interceptorDef = ASTHelper.newPrimitiveDefinitionNode(nodeFactory, interceptorCompName, interceptorDefRef);
		
		//Creating an interface with the same signature as the annotated one
		interceptorClt = ASTHelper.newClientInterfaceNode(nodeFactory, "c", itfDef.getName());
		final TypeInterface interceptorCltType = interceptorClt;
		InterfaceDefinitionDecorationHelper.setResolvedInterfaceDefinition(interceptorCltType, itfDef);

		//Creating an interface with the same signature as the annotated one
		interceptorSrv = ASTHelper.newServerInterfaceNode(nodeFactory, "s", itfDef.getName());
		final TypeInterface interceptorSrvType = interceptorSrv;
		InterfaceDefinitionDecorationHelper.setResolvedInterfaceDefinition(interceptorSrvType, itfDef);
		
		//Creating an interface to the scheduler
		final String registerTaskItfName = "exec.model.RegisterTask";
		InterfaceDefinition registerTaskItfDef = (InterfaceDefinition) idlLoader.load(registerTaskItfName, context);
		interceptorEventClt = ASTHelper.newClientInterfaceNode(nodeFactory, "push", registerTaskItfName);
		final TypeInterface eventCltType = interceptorEventClt;
		InterfaceDefinitionDecorationHelper.setResolvedInterfaceDefinition(eventCltType, registerTaskItfDef);

		//Creating an interface to be called from the scheduler
		final String executeTaskItfName = "exec.model.ExecuteTask";
		InterfaceDefinition executeTaskItfDef = (InterfaceDefinition) idlLoader.load(executeTaskItfName, context);
		final MindInterface eventSrv = ASTHelper.newServerInterfaceNode(nodeFactory, "pop", executeTaskItfName);
		final TypeInterface eventSrvType = eventSrv;
		InterfaceDefinitionDecorationHelper.setResolvedInterfaceDefinition(eventSrvType, executeTaskItfDef);
		
		//Adding the newly created interfaces to the interceptor definition
		((InterfaceContainer) interceptorDef).addInterface(interceptorClt);
		((InterfaceContainer) interceptorDef).addInterface(interceptorSrv);
		((InterfaceContainer) interceptorDef).addInterface(eventCltType);
		((InterfaceContainer) interceptorDef).addInterface(eventSrv);
		
		//Creating private data for our interceptor
		final StringBuilder dataCode = new StringBuilder();
		final StringTemplate dataST = getTemplate(ASYNCH_DATA_TEMPLATE_NAME,"PrivateDataDeclaration");
		dataST.setAttribute("interfaceDefinition", itfDef);
		dataCode.append(dataST.toString());
		//Adding the data to the definition of our interceptor-component
		final Data data = ASTHelper.newData(nodeFactory);
		((ImplementationContainer) interceptorDef).setData(data);
		data.setCCode(dataCode.toString());

		//Creating the source code for our interceptor-component
		final StringBuilder sourceCode = new StringBuilder();
		final StringTemplate sourceST = getTemplate(ASYNCH_SOURCE_TEMPLATE_NAME, "InterceptedServerDefinition");
		sourceST.setAttribute("interfaceDefinition", itfDef);
		sourceST.setAttribute("cltName", "c");
		sourceST.setAttribute("srvName", "s");
		sourceCode.append(sourceST.toString());
		//Adding the source to the definition of our interceptor-component
		final Source src = ASTHelper.newSource(nodeFactory);
		((ImplementationContainer) interceptorDef).addSource(src);
		src.setCCode(sourceCode.toString());	
		
		return interceptorDef;
	} 
}