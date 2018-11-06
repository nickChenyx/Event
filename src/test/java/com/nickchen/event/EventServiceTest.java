package com.nickchen.event;

import static org.junit.Assert.*;

public class EventServiceTest {

    @org.junit.Before
    public void setUp() throws Exception {
        EventService.init();
    }


    @org.junit.Test
    public void emitEvent() throws InterruptedException {
        EventService.registerEventHandler("hello", () -> System.out.println("hello goo."), () -> System.out.println("goodbye."));
        EventService.registerEventHandler("buy", () -> System.out.println("buy gold."), () -> System.out.println("business."));
        EventService.emitEvent("hello");
        EventService.emitEvent("buy");
        EventService.emitEvent("hello");
        EventService.emitEvent("buy");
        EventService.emitEvent("buy1");
        Thread.sleep(3000);
    }

    @org.junit.After
    public void tearDown() throws Exception {
        EventService.destroy();
    }

}