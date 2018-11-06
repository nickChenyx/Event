package com.nickchen.event;

/**
 * 事件处理接口，触发事件后的处理逻辑
 *
 * @author nickChen
 */
public interface EventHandler {

    /**
     * 事件出发后执行的方法
     */
    void resolve();
}
