/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020-2023 James Seibel
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

package com.seibel.distanthorizons.core.config.types;


import com.google.common.collect.BiMap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Table;
import com.seibel.distanthorizons.core.config.NumberUtil;
import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;
import com.seibel.distanthorizons.core.config.listeners.IConfigListener;
import com.seibel.distanthorizons.core.config.types.enums.EConfigEntryAppearance;
import com.seibel.distanthorizons.core.config.types.enums.EConfigEntryPerformance;
import com.seibel.distanthorizons.core.config.types.enums.EConfigEntryRelevantSide;
import com.seibel.distanthorizons.coreapi.interfaces.config.IConfigEntry;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Use for making the config variables
 * for types that are not supported by it look in ConfigBase
 *
 * @author coolGi
 * @version 2023-7-16
 */
public class ConfigEntry<T> extends AbstractConfigType<T, ConfigEntry<T>> implements IConfigEntry<T>
{
	private String comment;
	private T min;
	private T max;
	private final ArrayList<IConfigListener> listenerList;
	private final String serversideShortName;
	
	private final EConfigEntryPerformance performance;
	private final EConfigEntryRelevantSide relevantSide;
	
	// API control //
	/**
	 * If true this config can be controlled by the API <br>
	 * and any get() method calls will return the apiValue if it is set.
	 */
	public final boolean allowApiOverride;
	private T apiValue;
	
	
	
	/** Creates the entry */
	private ConfigEntry(
			EConfigEntryAppearance appearance, 
			T value, String comment, T min, T max, 
			String serversideShortName, boolean allowApiOverride, 
			EConfigEntryPerformance performance, EConfigEntryRelevantSide relevantSide, 
			ArrayList<IConfigListener> listenerList)
	{
		super(appearance, value);
		
		this.comment = comment;
		this.min = min;
		this.max = max;
		this.serversideShortName = serversideShortName;
		this.allowApiOverride = allowApiOverride;
		this.performance = performance;
		this.relevantSide = relevantSide;
		this.listenerList = listenerList;
	}
	
	
	
	/** Gets the default value of the option */
	@Override
	public T getDefaultValue() { return super.defaultValue; }
	
	@Override
	public void setApiValue(T newApiValue)
	{
		this.apiValue = newApiValue;
		this.listenerList.forEach(IConfigListener::onConfigValueSet);
	}
	@Override
	public T getApiValue() { return this.apiValue; }
	@Override
	public boolean getAllowApiOverride() { return this.allowApiOverride; }
	
	/** 
	 * DONT USE THIS IN YOUR CODE <br>
	 * Sets the value without informing the rest of the code (ie, doesnt call listeners, or saves the value). <br>
	 * Should only be used when loading the config from the file (in places like the {@link com.seibel.distanthorizons.core.config.file.ConfigFileHandling} or {@link com.seibel.distanthorizons.core.config.ConfigBase})
	 */
	public void pureSet(T newValue) {
		super.set(newValue);
	}
	
	/** Sets the value without saving */
	@Override
	public void setWithoutSaving(T newValue)
	{
		super.set(newValue);
		this.listenerList.forEach(IConfigListener::onConfigValueSet);
	}
	@Override
	public void set(T newValue)
	{
		this.setWithoutSaving(newValue);
		this.save();
	}
	
	public void uiSetWithoutSaving(T newValue)
	{
		this.setWithoutSaving(newValue);
		this.listenerList.forEach(IConfigListener::onUiModify);
	}
	public void uiSet(T newValue)
	{
		this.set(newValue);
		this.listenerList.forEach(IConfigListener::onUiModify);
	}
	
	
	@Override
	public T get()
	{
		if (this.allowApiOverride && this.apiValue != null)
		{
			return this.apiValue;
		}
		
		return super.get();
	}
	@Override
	public T getTrueValue()
	{
		return super.get();
	}
	
	
	/** Gets the min value */
	@Override
	public T getMin() { return this.min; }
	/** Sets the min value */
	@Override
	public void setMin(T newMin) { this.min = newMin; }
	/** Gets the max value */
	@Override
	public T getMax() { return this.max; }
	/** Sets the max value */
	@Override
	public void setMax(T newMax) { this.max = newMax; }
	/** Sets the min and max within a single setter */
	@Override
	public void setMinMax(T newMin, T newMax)
	{
		this.setMin(newMin);
		this.setMax(newMax);
	}
	
	/**
	 * Clamps the value within the set range
	 *
	 * @apiNote This does not save the value
	 */
	public void clampWithinRange() { this.clampWithinRange(this.min, this.max); }
	/**
	 * Clamps the value within a set range
	 *
	 * @param min The minimum that the value can be
	 * @param max The maximum that the value can be
	 * @apiNote This does not save the value
	 */
	@SuppressWarnings("unchecked") // Suppress due to its always safe
	public void clampWithinRange(T min, T max)
	{
		byte validness = this.isValid(min, max);
		if (validness == -1)
		{
			this.value = (T) NumberUtil.getMinimum(this.value.getClass());
		}
		if (validness == 1)
		{
			this.value = (T) NumberUtil.getMaximum(this.value.getClass());
		}
	}
	
	// TODO is this for command line use?
	public String getServersideShortName() { return this.serversideShortName; }
	
	@Override
	public String getComment() { return this.comment; }
	@Override
	public void setComment(String newComment) { this.comment = newComment; }
	
	/** Gets the performance impact of an option */
	public EConfigEntryPerformance getPerformance() { return this.performance; }
	/** Gets whether this config should apply to the client, server, or both */
	public EConfigEntryRelevantSide getRelevantSide() { return this.relevantSide; }
	
	/** Fired whenever the config value changes to a new value. */
	@Override public void addValueChangeListener(Consumer<T> onValueChangeFunc)
	{
		ConfigChangeListener<T> changeListener = new ConfigChangeListener<>(this, onValueChangeFunc);
		this.addListener(changeListener);
	}
	/** Fired whenever the config value is updated, including when the value doesn't change (IE when the UI changes state or the config is reloaded). */
	public void addListener(IConfigListener newListener) { this.listenerList.add(newListener); }
	
	//public void removeValueChangeListener(Consumer<T> onValueChangeFunc) { } // not currently implemented
	public void removeListener(IConfigListener oldListener) { this.listenerList.remove(oldListener); }
	
	public void clearListeners() { this.listenerList.clear(); }
	public ArrayList<IConfigListener> getListeners() { return this.listenerList; }
	/** Replaces the listener list */
	public void setListeners(ArrayList<IConfigListener> newListeners)
	{
		this.listenerList.clear();
		this.listenerList.addAll(newListeners);
	}
	public void setListeners(IConfigListener... newListeners) { this.listenerList.addAll(Arrays.asList(newListeners)); }
	
	
	/**
	 * Checks if the option is valid
	 *
	 * @return 0 == valid
	 * <p>  2 == invalid
	 * <p>  1 == number too high
	 * <p> -1 == number too low
	 */
	@Override
	public byte isValid() { return this.isValid(this.value, this.min, this.max); }
	/**
	 * Checks if a new value is valid
	 *
	 * @param value Value that is being checked whether valid
	 * @return 0 == valid
	 * <p>  2 == invalid
	 * <p>  1 == number too high
	 * <p> -1 == number too low
	 */
	@Override
	public byte isValid(T value) { return this.isValid(value, this.min, this.max); }
	/**
	 * Checks if a new value is valid
	 *
	 * @param min The minimum that the value can be
	 * @param max The maximum that the value can be
	 * @return 0 == valid
	 * <p>  2 == invalid
	 * <p>  1 == number too high
	 * <p> -1 == number too low
	 */
	public byte isValid(T min, T max) { return this.isValid(this.value, min, max); }
	/**
	 * Checks if a new value is valid
	 *
	 * @param value Value that is being checked whether valid
	 * @param min The minimum that the value can be
	 * @param max The maximum that the value can be
	 * @return 0 == valid
	 * <p>  2 == invalid
	 * <p>  1 == number too high
	 * <p> -1 == number too low
	 */
	public byte isValid(T value, T min, T max)
	{
		if (this.configBase.disableMinMax)
		{
			return 0;
		}
		else if (min == null && max == null)
		{
			// no validation is needed for this field
			return 0;
		}
		else if (value == null || this.value == null
				|| value.getClass() != this.value.getClass())
		{
			// If the 2 variables aren't the same type then it will be invalid
			return 2;
		}
		else if (Number.class.isAssignableFrom(value.getClass()))
		{ 
			// Only check min max if it is a number
			if (max != null && NumberUtil.greaterThan((Number) value, (Number) max))
			{
				return 1;
			}
			if (min != null && NumberUtil.lessThan((Number) value, (Number) min))
			{
				return -1;
			}
			
			return 0;
		}
		else
		{
			return 0;
		}
	}
	
	/** This should normally not be called since set() automatically calls this */
	public void save() { this.configBase.configFileINSTANCE.saveEntry(this); }
	/** This should normally not be called except for special circumstances */
	public void load() { this.configBase.configFileINSTANCE.loadEntry(this); }
	
	
	@Override
	public boolean equals(IConfigEntry<?> obj) { return obj.getClass() == ConfigEntry.class && this.equals((ConfigEntry<?>) obj); }
	/** Is the value of this equal to another */
	public boolean equals(ConfigEntry<?> obj)
	{
		// Can all of this just be "return this.value.equals(obj.value)"?
		
		if (Number.class.isAssignableFrom(this.value.getClass()))
		{
			return this.value == obj.value;
		}
		else
		{
			return this.value.equals(obj.value);
		}
	}
	
	public static class Builder<TValue> extends AbstractConfigType.Builder<TValue, Builder<TValue>>
	{
		private String tmpComment = null;
		private TValue tmpMin = null;
		private TValue tmpMax = null;
		protected String tmpServersideShortName = null;
		private boolean tmpUseApiOverwrite = true;
		private EConfigEntryPerformance tmpPerformance = EConfigEntryPerformance.DONT_SHOW;
		private EConfigEntryRelevantSide tmpRelevantSide = EConfigEntryRelevantSide.CLIENT;
		protected ArrayList<IConfigListener> tmpIConfigListener = new ArrayList<>();
		
		private Supplier<TValue> computedValueGetter;
		private Consumer<TValue> computedValueSetter;
		private boolean preventAutoApplyUntilExit;
		
		public Builder<TValue> comment(String newComment)
		{
			this.tmpComment = newComment;
			return this;
		}
		
		public Builder<TValue> computed(Supplier<TValue> computedValueGetter, Consumer<TValue> computedValueSetter)
		{
			this.computedValueGetter = computedValueGetter;
			this.computedValueSetter = computedValueSetter;
			this.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI);
			return this;
		}
		
		public Builder<TValue> linkedTo(DeferredAccessor<TValue> otherConfigAccessor)
		{
			return this.computed(
					otherConfigAccessor::get,
					otherConfigAccessor::set
			);
		}
		
		public Builder<TValue> preset(PresetBuilder<TValue> presetBuilder)
		{
			return this.computed(
					() -> {
						List<TValue> values = presetBuilder.presetValues.entrySet().stream()
								.map(entry -> entry.getValue().inverse().get(entry.getKey().get()))
								.distinct().limit(2)
								.collect(Collectors.toList());
						
						if (values.size() != 1)
						{
							return presetBuilder.customStateValue;
						}
						
						return values.get(0);
					},
					value -> {
						for (Map.Entry<DeferredAccessor<Object>, BiMap<TValue, Object>> entry : presetBuilder.presetValues.entrySet())
						{
							entry.getKey().set(entry.getValue().get(value));
						}
					}
			);
		}
		
		/** Allows most values to be set by 1 setter */
		public Builder<TValue> setMinDefaultMax(TValue newMin, TValue newDefault, TValue newMax)
		{
			this.set(newDefault);
			this.setMinMax(newMin, newMax);
			return this;
		}
		
		public Builder<TValue> setMinMax(TValue newMin, TValue newMax)
		{
			this.tmpMin = newMin;
			this.tmpMax = newMax;
			return this;
		}
		
		public Builder<TValue> setMin(TValue newMin)
		{
			this.tmpMin = newMin;
			return this;
		}
		
		public Builder<TValue> setMax(TValue newMax)
		{
			this.tmpMax = newMax;
			return this;
		}
		
		public Builder<TValue> setServersideShortName(String name)
		{
			this.tmpServersideShortName = name;
			return this;
		}
		
		public Builder<TValue> setUseApiOverwrite(boolean newUseApiOverwrite)
		{
			this.tmpUseApiOverwrite = newUseApiOverwrite;
			return this;
		}
		
		public Builder<TValue> setPerformance(EConfigEntryPerformance newPerformance)
		{
			this.tmpPerformance = newPerformance;
			return this;
		}
		
		public Builder<TValue> setSide(EConfigEntryRelevantSide relevantSide)
		{
			this.tmpRelevantSide = relevantSide;
			return this;
		}
		
		
		
		public Builder<TValue> replaceListeners(ArrayList<IConfigListener> newConfigListener)
		{
			this.tmpIConfigListener = newConfigListener;
			return this;
		}
		
		public Builder<TValue> addListeners(IConfigListener... newConfigListener)
		{
			this.tmpIConfigListener.addAll(Arrays.asList(newConfigListener));
			return this;
		}
		
		public Builder<TValue> addListener(IConfigListener newConfigListener)
		{
			this.tmpIConfigListener.add(newConfigListener);
			return this;
		}
		
		public Builder<TValue> clearListeners()
		{
			this.tmpIConfigListener.clear();
			return this;
		}
		
		
		
		public ConfigEntry<TValue> build()
		{
			return new ConfigEntry<>(
					this.tmpAppearance, 
					this.tmpValue, this.tmpComment, this.tmpMin, this.tmpMax, 
					this.tmpServersideShortName, this.tmpUseApiOverwrite, 
					this.tmpPerformance, this.tmpRelevantSide, this.tmpIConfigListener);
		}
		
	}
	
	/**
	 * Wraps deferred accesses to a ConfigEntry. <br>
	 * It replaces the direct uses of ConfigEntry in places where Java runtime should not attempt
	 * to dereference path to said entry, until initialization is done.
	 */
	@FunctionalInterface
	public interface DeferredAccessor<T>
	{
		ConfigEntry<T> getEntry();
		
		default T get() { return this.getEntry().get(); }
		default void set(T value) { this.getEntry().set(value); }
		
	}
	
	public static class PresetBuilder<TValue>
	{
		public final TValue customStateValue;
		public final Map<DeferredAccessor<Object>, BiMap<TValue, Object>> presetValues = new HashMap<>();
		
		public PresetBuilder(TValue customStateValue)
		{
			this.customStateValue = customStateValue;
		}
		
		@SuppressWarnings("unchecked")
		public <TControlledEntryValue> PresetBuilder<TValue> addConfigEntry(
				DeferredAccessor<TControlledEntryValue> controlledEntrySupplier,
				Consumer<Map<TValue, TControlledEntryValue>> presetValueInitializer
		)
		{
			presetValueInitializer.accept(
					(Map<TValue, TControlledEntryValue>) this.presetValues.computeIfAbsent(
							(DeferredAccessor<Object>) controlledEntrySupplier,
							ignored -> HashBiMap.create()
					)
			);
			return this;
		}
		
	}
	
}
