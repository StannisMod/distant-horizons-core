/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class ImmersivePortalsAccessor implements IImmersivePortalsAccessor
{
	
	private static MethodHandle isRendering;
	
	private static long lastPortalTime = -1;
	private static boolean portalVisible = false;
	
	public ImmersivePortalsAccessor() {
		DhApi.events.bind(DhApiBeforeRenderEvent.class, BeforeRender.INSTANCE);
	}
	
	@Override
	public boolean isRenderingPortal()
	{
		try {
			if (isRendering == null) {
				isRendering = MethodHandles.lookup().findStatic(
					#if MC_VER > MC_1_16_5
					Class.forName("qouteall.imm_ptl.core.render.context_management.PortalRendering"),
					#else
					Class.forName("com.qouteall.immersive_portals.render.context_management.PortalRendering"),
					#endif
					"isRendering", MethodType.methodType(Boolean.TYPE)
				);
			}
			boolean result = (boolean) isRendering.invoke();
			if (result) {
				portalVisible = true;
				lastPortalTime = System.currentTimeMillis();
			} else if (portalVisible) {
				if (System.currentTimeMillis() - lastPortalTime > 1000) {
					portalVisible = false;
				}
			}
			return result;
		}
		catch (Throwable e)
		{
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public boolean hasPortalOnScreen() {
		return portalVisible;
	}
	
	@Override
	public String getModName()
	{
		return "ImmersivePortalsMod";
	}
	
	
	public static class BeforeRender extends DhApiBeforeRenderEvent
	{
		
		public static BeforeRender INSTANCE = new BeforeRender();
		
		private BeforeRender() {}
		
		@Override
		public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event)
		{
			if (ModAccessorInjector.INSTANCE.get(IImmersivePortalsAccessor.class).isRenderingPortal()) {
				event.cancelEvent();
			}
		}
		
	}
	
}
