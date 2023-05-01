package com.seibel.lod.core.logging.f3;

import com.seibel.lod.coreapi.ModInfo;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

public class F3Screen
{
	public static boolean renderCustomF3 = true;
	
	private static final String[] DEFAULT_STR = {
			"",
			ModInfo.READABLE_NAME + " version: " + ModInfo.VERSION
	};
	private static final LinkedList<WeakReference<Message>> selfUpdateMessages = new LinkedList<>();
	
	public static void addStringToDisplay(List<String> list)
	{
		list.addAll(Arrays.asList(DEFAULT_STR));
		Iterator<WeakReference<Message>> iterator = selfUpdateMessages.iterator();
		while (iterator.hasNext())
		{
			WeakReference<Message> ref = iterator.next();
			Message message = ref.get();
			if (message == null)
			{
				iterator.remove();
			}
			else
			{
				message.printTo(list);
			}
		}
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	public static abstract class Message
	{
		protected Message()
		{
			selfUpdateMessages.add(new WeakReference<>(this));
		}
		
		public abstract void printTo(List<String> output);
	}
	
	@SuppressWarnings("unused")
	public static class StaticMessage extends Message
	{
		private final String[] lines;
		
		public StaticMessage(String... lines)
		{
			this.lines = lines;
		}
		
		@Override
		public void printTo(List<String> output)
		{
			output.addAll(Arrays.asList(lines));
		}
	}
	
	@SuppressWarnings("unused")
	public static class DynamicMessage extends Message
	{
		private final Supplier<String> supplier;
		
		public DynamicMessage(Supplier<String> message)
		{
			this.supplier = message;
		}
		
		public void printTo(List<String> list)
		{
			String msg = supplier.get();
			if (msg != null)
			{
				list.add(msg);
			}
		}
	}
	
	@SuppressWarnings("unused")
	public static class MultiDynamicMessage extends Message
	{
		private final Supplier<String>[] supplier;
		
		@SafeVarargs
		public MultiDynamicMessage(Supplier<String>... messages)
		{
			this.supplier = messages;
		}
		
		public void printTo(List<String> list)
		{
			for (Supplier<String> s : supplier)
			{
				String msg = s.get();
				if (msg != null)
				{
					list.add(msg);
				}
			}
		}
	}
	
	public static class NestedMessage extends Message
	{
		private final Supplier<String[]> supplier;
		
		public NestedMessage(Supplier<String[]> message)
		{
			this.supplier = message;
		}
		
		public void printTo(List<String> list)
		{
			String[] msg = supplier.get();
			if (msg != null)
			{
				list.addAll(Arrays.asList(msg));
			}
		}
	}
	
}
