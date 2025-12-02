package com.github.games647.fastlogin.bukkit.scheduler.callback;

@SuppressWarnings("unused")
public interface CallBack<T> {
    T getCallBack();

    void setCallBack(T callback);
}