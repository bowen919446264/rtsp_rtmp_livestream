package me.lake.librestreaming.client;

import me.lake.librestreaming.core.listener.RESConnectionListener;
import me.lake.librestreaming.model.RESCoreParameters;

/**
 * 发送器的命名
 * Created by liuwb on 2017/4/26.
 */
public interface ISender {

    void prepare(RESCoreParameters coreParameters);

    void setConnectionListener(RESConnectionListener connectionListener);

    void start(String addr);

    void stop();

    void destroy();
}
