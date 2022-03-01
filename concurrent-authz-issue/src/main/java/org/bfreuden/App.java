package org.bfreuden;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

import java.util.concurrent.CountDownLatch;

public class App {
    static final int EVENT_LOOP_THREADS = 16;
    static Vertx VERTX = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(EVENT_LOOP_THREADS));
    static final int PORT = 8888;

    public static void main( String[] args ) throws InterruptedException {
        // deploy a web server running on multiple threads
        CountDownLatch countDownLatch = new CountDownLatch(1);
        VERTX.deployVerticle(WebServer.class.getName(), new DeploymentOptions().setInstances(EVENT_LOOP_THREADS)).onComplete(v -> countDownLatch.countDown());
        countDownLatch.await();
    }
}
