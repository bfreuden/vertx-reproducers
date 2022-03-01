package org.bfreuden;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;

import java.util.concurrent.CountDownLatch;

public class App
{
    static Vertx vertx = Vertx.vertx();
    static final int PORT = 8888;

    public static void main( String[] args ) throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        vertx.deployVerticle(WebServer.class.getName(), new DeploymentOptions().setInstances(16)).onComplete(v -> countDownLatch.countDown());
        countDownLatch.await();
    }
}
