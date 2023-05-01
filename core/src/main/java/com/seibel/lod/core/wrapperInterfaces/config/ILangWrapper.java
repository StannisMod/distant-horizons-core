package com.seibel.lod.core.wrapperInterfaces.config;

import com.seibel.lod.coreapi.interfaces.dependencyInjection.IBindable;

public interface ILangWrapper extends IBindable {

    boolean langExists(String str);

    String getLang(String str);
}
