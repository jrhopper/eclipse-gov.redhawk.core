/*******************************************************************************
 * This file is protected by Copyright.
 * Please refer to the COPYRIGHT file distributed with this source distribution.
 *
 * This file is part of REDHAWK IDE.
 *
 * All rights reserved.  This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package gov.redhawk.ui.port.nxmblocks;

import gov.redhawk.sca.util.Debug;
import gov.redhawk.ui.port.nxmplot.AbstractNxmPlotWidget;
import gov.redhawk.ui.port.nxmplot.PlotActivator;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import nxm.sys.lib.NeXtMidas;
import nxm.sys.lib.Table;
import nxm.sys.prim.plot;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PlatformUI;

import BULKIO.StreamSRI;

/**
 * @noreference This class is provisional/beta and is subject to API changes
 * @since 4.4
 */
public class PlotNxmBlock extends AbstractNxmBlock<plot> {

	private static final Debug TRACE_LOG = new Debug(PlotActivator.PLUGIN_ID, PlotNxmBlock.class.getSimpleName());
	
	private PlotNxmBlockSettings settings;
	private ConcurrentHashMap<String, String> streamIdToSourceNameMap = new ConcurrentHashMap<String, String>();
	private ConcurrentHashMap<String, StreamSRI> streamIdToSriMap = new ConcurrentHashMap<String, StreamSRI>();
	private IMenuManager menu;

	public PlotNxmBlock(@NonNull AbstractNxmPlotWidget plotWidget, PlotNxmBlockSettings settings) {
		super(plot.class, PlotNxmBlockSettings.class, "PLOT", plotWidget);
		if (settings == null) {
			settings = new PlotNxmBlockSettings();
		} else {
			// PASS: TODO: clone settings?
		}
		this.settings = settings;
	}

	@Override
	public int getMaxInputs() {
		return 1; // only one is support here, create multiple instances for multiple ports
	}

	@Override
	public int getMaxOutputs() {
		return 0; // this is the end point to plot so it has no outputs
	}

	@Override
	public void launch(String streamID, StreamSRI sri) {
		TRACE_LOG.enteringMethod(streamID, sri);
		checkLaunchParams(streamID, sri);

		BlockIndexPair inputBlockInfo = this.getInputBlockInfo(0);
		if (inputBlockInfo == null) {
			throw new IllegalStateException("A input index 0 must be set before launch() can be called.");
		}
		
		final AbstractNxmPlotWidget currentPlotWidget = getContext();
		final String sourceName = inputBlockInfo.getBlock().getOutputName(inputBlockInfo.getIndex(), streamID);

//		StringBuilder pipeQualifiers = new StringBuilder();
//		Integer frameSize = settings.getFrameSize();
//		if (frameSize != null && frameSize > 0) {          // 1. override frame size with value in settings
//			pipeQualifiers.append("{FRAMESIZE=").append(frameSize).append('}');
//		} else {
//			if (sri != null) {                             // 2. check sri.subsize
//				frameSize = sri.subsize;
//			}
//			String tmpResName = AbstractNxmPlotWidget.createUniqueName(false);
//			currentPlotWidget.runGlobalCommand("TABLE " + tmpResName + " CREATE");
//			currentPlotWidget.runGlobalCommand("STATUS/VERBOSE " + sourceName + " typeCodeClass=" + tmpResName + ".TYPECODECLASS  frameSize=" + tmpResName + ".FRAMESIZE");
//			if (TRACE_LOG.enabled) {
//				currentPlotWidget.runGlobalCommand("RESULTS/ALL " + tmpResName);
//				currentPlotWidget.runGlobalCommand("STATUS/VERBOSE " + sourceName);
//			}
//			currentPlotWidget.runGlobalCommand("RESULTS/GLOBAL " + tmpResName + " " + tmpResName); // put in global results table
//			Table statusResults = NeXtMidas.getGlobalInstance().getMidasContext().getResults().getTable(tmpResName);
//			currentPlotWidget.runGlobalCommand("REMOVE " + tmpResName);        // cleanup tmp results
//			currentPlotWidget.runGlobalCommand("REMOVE/GLOBAL " + tmpResName); // cleanup tmp results
//			int typeCodeClass = 1;
//			if (statusResults != null) {
//				typeCodeClass = statusResults.getL("TYPECODECLASS", typeCodeClass);
//				frameSize = statusResults.getL("FRAMESIZE", frameSize);
//			}
//			if (typeCodeClass == 1 && frameSize <= 0) {    // 3. no frame size and type 1000 data
//				pipeQualifiers.append("{FRAMESIZE=1024}"); //    frame to 1024
//			}
//		}
//
//		final Integer pipeSize = settings.getPipeSize();
//		if (pipeSize != null && pipeSize > 0) {
//			pipeQualifiers.append("{PIPESIZE=").append(pipeSize).append('}');
//		}
		
		final String pipeQuals = getPipeQualifiers(settings, sri, currentPlotWidget, sourceName);
		currentPlotWidget.addSource(sourceName, pipeQuals, null);

		streamIdToSourceNameMap.put(streamID, sourceName); // save mapping for shutdown, apply settings, etc.
		streamIdToSriMap.put(streamID, sri);

		final IMenuManager menuManager = this.menu;
		if (menuManager != null) {
			IAction action = new Action(streamID, IAction.AS_CHECK_BOX) {
				@Override
				public void run() {
					String layerEnableOption = isChecked() ? "+GLOBAL" : "-GLOBAL"; // show/hide layer (i.e source stream)
					String setViaMsgName = "SET.LAYERS." + sourceName + ".enable";
					currentPlotWidget.sendPlotMessage(setViaMsgName, 0, layerEnableOption);
				}
			};
			action.setChecked(true);
			action.setId(streamID);
			menuManager.add(action);
		}
		// FYI: this is end point, so it DOES NOT have any follow on blocks
		TRACE_LOG.exitingMethod(streamID);
	}

	@Override
	public void shutdown(final String streamID) {
		TRACE_LOG.enteringMethod(streamID);
		final AbstractNxmPlotWidget currentPlotWidget = getContext();
		if (currentPlotWidget == null) {
			throw new IllegalStateException("A context (AbstractNxmPlotWidget) must be set before shutdown() can be called.");
		}

		String sourceName = streamIdToSourceNameMap.remove(streamID);
		if (sourceName != null) {
			currentPlotWidget.removeSource(sourceName);
		}
		streamIdToSriMap.remove(streamID);
		
		final IMenuManager menuManager = this.menu;
		if (menuManager != null) {
			PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
				@Override
				public void run() {
					menuManager.remove(streamID);
				}
			});
			
//			new UIJob("remove stream from menu") {
//				@Override
//				public IStatus runInUIThread(IProgressMonitor monitor) {
//					menuManager.remove(streamID);
//					return Status.OK_STATUS;
//				}
//			}
//			.schedule(0);
		}
	}

	@Override
	public void stop() {
		TRACE_LOG.enteringMethod("curSize=" + streamIdToSourceNameMap.size());
		final AbstractNxmPlotWidget currentPlotWidget = getContext();
		if (currentPlotWidget == null) {
			throw new IllegalStateException("A context (AbstractNxmPlotWidget) must be set before stop() can be called.");
		}
		
		// remove all our streamIDs from plot widget
		Iterator<String> valuesIter = streamIdToSourceNameMap.values().iterator();
		while (valuesIter.hasNext()) {
			String sourceName = valuesIter.next();
			currentPlotWidget.removeSource(sourceName);
			valuesIter.remove();
		}
		streamIdToSriMap.clear();
	}

	@Override
	public boolean hasControls() {
		return true;
	}

	@Override
	public void createControls(Composite parent, Object settings, DataBindingContext dataBindingContext) {
		PlotNxmBlockSettings blockSettings = null;
		if (settings instanceof PlotNxmBlockSettings) {
			blockSettings = (PlotNxmBlockSettings) settings;
		}
		new PlotNxmBlockControls(blockSettings, dataBindingContext).createControls(parent);
	}

	@Override
	public PlotNxmBlockSettings getSettings() {
		return settings.clone();
	}

	@Override
	public void contributeMenuItems(IMenuManager menu) {
		this.menu = menu;
	}
	
	@Override
	public void applySettings(Object settings, String streamId) {
		if (settings instanceof PlotNxmBlockSettings) {
			if (streamId != null) { // apply to specified stream
				applySettingsTo(null, settings, streamId);
			} else {                // apply to all stream IDs
				Iterator<String> keyIter = streamIdToSourceNameMap.keySet().iterator();
				while (keyIter.hasNext()) {
					streamId = keyIter.next();
					applySettingsTo(null, settings, streamId);
				}
			}
		}
	}

	@Override
	protected void applySettingsTo(plot cmd, Object settings, String streamId) {
		if (settings instanceof PlotNxmBlockSettings) {
			PlotNxmBlockSettings newSettings = (PlotNxmBlockSettings) settings;
			
			if (!newSettings.equals(this.settings)) {
				this.settings.setFrameSize(newSettings.getFrameSize());
				this.settings.setPipeSize(newSettings.getPipeSize());
				
				String sourceName = streamIdToSourceNameMap.get(streamId);
				StreamSRI sri = streamIdToSriMap.get(streamId);
				
				AbstractNxmPlotWidget plotWidget = getContext();
				String pipeQuals = getPipeQualifiers(this.settings, sri, plotWidget, sourceName);
				plotWidget.removeSource(sourceName);
				plotWidget.addSource(sourceName, pipeQuals, null);
			}
		}
	}

	@Override
	protected String formCmdLine(AbstractNxmPlotWidget plotWidget, String streamID) {
		return null; // null for no Command to execute
	}

	private String getPipeQualifiers(PlotNxmBlockSettings settings, StreamSRI sri, AbstractNxmPlotWidget plotWidget, String sourceName) {
		StringBuilder pipeQualifiers = new StringBuilder();
		Integer frameSize = settings.getFrameSize();
		if (frameSize != null && frameSize > 0) {          // 1. override frame size with value in settings
			pipeQualifiers.append("{FRAMESIZE=").append(frameSize).append('}');
		} else {
			if (sri != null) {                             // 2. check sri.subsize
				frameSize = sri.subsize;
			}
			String tmpResName = AbstractNxmPlotWidget.createUniqueName(false);
			plotWidget.runGlobalCommand("TABLE " + tmpResName + " CREATE");
			plotWidget.runGlobalCommand("STATUS/VERBOSE " + sourceName + " typeCodeClass=" + tmpResName + ".TYPECODECLASS  frameSize=" + tmpResName + ".FRAMESIZE");
			if (TRACE_LOG.enabled) {
				plotWidget.runGlobalCommand("RESULTS/ALL " + tmpResName);
				plotWidget.runGlobalCommand("STATUS/VERBOSE " + sourceName);
			}
			plotWidget.runGlobalCommand("RESULTS/GLOBAL " + tmpResName + " " + tmpResName); // put in global results table
			Table statusResults = NeXtMidas.getGlobalInstance().getMidasContext().getResults().getTable(tmpResName);
			plotWidget.runGlobalCommand("REMOVE " + tmpResName);        // cleanup tmp results
			plotWidget.runGlobalCommand("REMOVE/GLOBAL " + tmpResName); // cleanup tmp results
			int typeCodeClass = 1;
			if (statusResults != null) {
				typeCodeClass = statusResults.getL("TYPECODECLASS", typeCodeClass);
				frameSize = statusResults.getL("FRAMESIZE", frameSize);
			}
			if (typeCodeClass == 1 && frameSize <= 0) {    // 3. no frame size and type 1000 data
				pipeQualifiers.append("{FRAMESIZE=1024}"); //    frame to 1024
			}
		}
		
		final Integer pipeSize = settings.getPipeSize();
		if (pipeSize != null && pipeSize > 0) {
			pipeQualifiers.append("{PIPESIZE=").append(pipeSize).append('}');
		}
		return pipeQualifiers.toString();
	}
}