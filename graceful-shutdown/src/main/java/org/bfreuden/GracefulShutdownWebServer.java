package org.bfreuden;

import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.concurrent.CountDownLatch;

public class GracefulShutdownWebServer extends AbstractVerticle {

    private int inflightRequests = 0;
    private Promise<Void> stopPromise;
    private HttpServer httpServer;

    public void start() {
        Router router = Router.router(vertx);
        Route route = router.route();
        route.handler(this::maybeReply503DuringIfShuttingDown);
        route.handler(this::requestHandler);
        // there is probably something to do with the error handler?
        int port = 9000;
        httpServer = vertx.createHttpServer().requestHandler(router);
        httpServer.listen(port);
        System.out.println("HTTP server running on port " + port);
    }


    private void maybeReply503DuringIfShuttingDown(RoutingContext routingContext) {
        if (stopPromise != null) {
            routingContext.response().setStatusCode(503).end("KO");
        } else {
            inflightRequests++;
            routingContext.next();
        }
    }

    private void requestHandler(RoutingContext routingContext) {
        String delay = routingContext.request().getParam("delay", "1");
        vertx.setTimer(Integer.parseInt(delay)* 1000L, ignored -> {
            routingContext.response().setStatusCode(200).end("OK", this::maybeShutdownServer);
        });
    }

    private void maybeShutdownServer(AsyncResult<Void> responseWritten) {
        inflightRequests--;
        if (stopPromise != null) {
            if (inflightRequests == 0) {
                System.out.println("No in-flight requests");
                shutdown();
            } else {
                System.out.println("Shutdown delayed, number of in-flight request(s): " + inflightRequests);
            }
        }
    }

    private void shutdown() {
        if (httpServer != null) {
            httpServer.close().onComplete(ar -> shutdownComplete());
        } else {
            shutdownComplete();
        }
    }

    private void shutdownComplete() {
        System.out.println("Shutdown complete");
        stopPromise.complete();
    }


    public void stop(Promise<Void> stop) {
        System.out.println("Graceful shutdown required, number of in-flight request(s): " + inflightRequests);
        // it doesn't seem to be so clean
        // System.out.println("Until shutdown is complete, server will refuse connections");
        // httpServer.requestStream().pause();
        this.stopPromise = stop;
        if (httpServer == null || inflightRequests == 0) {
            shutdown();
        }
    }

    public static void main(String[] args) {
        CountDownLatch latch = new CountDownLatch(1);
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(GracefulShutdownWebServer.class, new DeploymentOptions().setInstances(1))
                .onFailure(error -> {
                    error.printStackTrace();
                    vertx.close();
                } )
                .onSuccess(deploymentId -> {
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        System.out.println("Undeploying verticle...");
                        vertx.undeploy(deploymentId).onComplete(ar -> latch.countDown());
                        try {
                            System.out.println("Waiting for verticle to undeploy");
                            latch.await();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }));
                });
    }

}

