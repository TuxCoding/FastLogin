package com.github.games647.fastlogin.bukkit.scheduler.task;

import com.github.games647.fastlogin.bukkit.scheduler.callback.CallBack;

import java.util.concurrent.CompletableFuture;

public class TaskCallback <T> implements CallBack<T> {
    private final CompletableFuture<Boolean> lock = new CompletableFuture<>();
    private T callback;

    @Override
    public T getCallBack() {
        this.lock.join();
        return this.callback;
    }

    @Override
    public void setCallBack(T callback) {
        this.lock.complete(true);
        this.callback = callback;
    }
}