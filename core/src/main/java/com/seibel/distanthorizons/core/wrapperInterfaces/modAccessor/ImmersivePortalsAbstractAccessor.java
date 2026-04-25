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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.Supplier;

public abstract class ImmersivePortalsAbstractAccessor implements IImmersivePortalsAccessor
{
	
	private static Class<?> portal;
	private static MethodHandle isRendering;
	private static Method shouldSkipRenderingPortal;
	private static MethodHandle getGlobalPortals;
	
	private static long lastPortalTime = -1;
	private static boolean portalVisible = false;
	
	
	public ImmersivePortalsAbstractAccessor() {
		DhApi.events.bind(DhApiBeforeRenderEvent.class, BeforeRender.INSTANCE);
	}
	
	private static Class<?> getPortalRenderingClass() {
		try {
			return Class.forName("qouteall.imm_ptl.core.render.context_management.PortalRendering");
		} catch (ClassNotFoundException first) {
			try {
				return Class.forName("com.qouteall.immersive_portals.render.context_management.PortalRendering"); // 1.16
			} catch (ClassNotFoundException second) {
				RuntimeException err = new RuntimeException(first);
				err.addSuppressed(second);
				throw err;
			}
		}
	}
	
	private static Class<?> getPortalRendererClass() {
		try {
			return Class.forName("qouteall.imm_ptl.core.render.renderer.PortalRenderer"); // 1.21+
		} catch (ClassNotFoundException first) {
			try {
				return Class.forName("qouteall.imm_ptl.core.render.PortalRenderer");
			} catch (ClassNotFoundException second) {
				try {
					return Class.forName("com.qouteall.immersive_portals.render.PortalRenderer"); // 1.16
				} catch (ClassNotFoundException third) {
					RuntimeException err = new RuntimeException(first);
					err.addSuppressed(second);
					err.addSuppressed(third);
					throw err;
				}
			}
		}
	}
	
	private static Class<?> getPortalClass() {
		try {
			portal = Class.forName("qouteall.imm_ptl.core.portal.Portal");
		} catch (ClassNotFoundException first) {
			try {
				portal = Class.forName("com.qouteall.immersive_portals.portal.Portal"); // 1.16
			} catch (ClassNotFoundException second) {
				RuntimeException err = new RuntimeException(first);
				err.addSuppressed(second);
				throw err;
			}
		}
		return portal;
	}
	
	private static Class<?> getGlobalPortalStorageClass() {
		try {
			return Class.forName("qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage");
		} catch (ClassNotFoundException first) {
			try {
				return Class.forName("com.qouteall.immersive_portals.McHelper"); // 1.16
			} catch (ClassNotFoundException second) {
				RuntimeException err = new RuntimeException(first);
				err.addSuppressed(second);
				throw err;
			}
		}
	}
	
	private static Class<?> getIPCGlobalClass() {
		try {
			return Class.forName("qouteall.imm_ptl.core.IPCGlobal");
		} catch (ClassNotFoundException first) {
			try {
				return Class.forName("com.qouteall.immersive_portals.CGlobal"); // 1.16
			} catch (ClassNotFoundException second) {
				RuntimeException err = new RuntimeException(first);
				err.addSuppressed(second);
				throw err;
			}
		}
	}
	
	private static boolean shouldSkipRenderingPortal(Object portal, Supplier<?> frustumSupplier) {
		try {
			if (shouldSkipRenderingPortal == null) {
				shouldSkipRenderingPortal = getPortalRendererClass().getDeclaredMethod(
					"shouldSkipRenderingPortal", getPortalClass(), Supplier.class
				);
				shouldSkipRenderingPortal.setAccessible(true);
			}
			if (Modifier.isStatic(shouldSkipRenderingPortal.getModifiers())) {
				return (boolean) shouldSkipRenderingPortal.invoke(null, portal, frustumSupplier);
			} else {
				return (boolean) shouldSkipRenderingPortal.invoke(
					getIPCGlobalClass().getField("renderer").get(null), portal, frustumSupplier
				);
			}
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
	
	protected abstract Object getClientLevel();
	
	protected abstract Class<?> getLevelClass();
	
	protected abstract Iterable<?> getEntitiesForRendering();
	
	protected abstract Supplier<?> getFrustumSupplier();
	
	private static boolean isPortal(Object object) {
		return getPortalClass().isInstance(object);
	}
	
	private List<?> getGlobalPortals(Object level) {
		try {
			if (getGlobalPortals == null) {
				getGlobalPortals = MethodHandles.lookup().findStatic(
					getGlobalPortalStorageClass(),
					"getGlobalPortals", MethodType.methodType(List.class).appendParameterTypes(
						getLevelClass()
					)
				);
			}
			return (List<?>) getGlobalPortals.invoke(level);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public boolean isRenderingPortal()
	{
		try {
			if (isRendering == null) {
				isRendering = MethodHandles.lookup().findStatic(
					getPortalRenderingClass(),
					"isRendering", MethodType.methodType(Boolean.TYPE)
				);
			}
			return (boolean) isRendering.invoke();
		}
		catch (Throwable e)
		{
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Essentially reimplements PortalRenderer::getPortalsToRender because it did not exist in 1.16.
	 */
	private boolean isPortalVisibleRightNow() {
		Supplier<?> frustumSupplier = getFrustumSupplier();
		if (frustumSupplier == null) return false;
		for (Object portal : getGlobalPortals(getClientLevel())) {
			if (!shouldSkipRenderingPortal(portal, frustumSupplier)) {
				return true;
			}
		}
		for (Object entity : getEntitiesForRendering()) {
			if (isPortal(entity) && !shouldSkipRenderingPortal(entity, frustumSupplier)) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public boolean wasPortalRecentlyVisible() {
		// I did consider setting portalVisible to true whenever PortalRendering::isRendering was true instead,
		// but that would still render fading immediately after startup before entering the portal at least once.
		// This is more robust, but slightly worse for performance. Still, people can just turn fading off if they have issues.
		boolean isPortalVisible = isPortalVisibleRightNow();
		if (isPortalVisible) {
			lastPortalTime = System.currentTimeMillis();
			portalVisible = true;
		} else if (portalVisible) {
			if (System.currentTimeMillis() - lastPortalTime > 1000) {
				portalVisible = false;
			}
		}
		// Simply checking portal visibility right now is not sufficient, that will still render the fading on top of the portal.
		// Instead, we check if a portal was rendered during the last second or so.
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
